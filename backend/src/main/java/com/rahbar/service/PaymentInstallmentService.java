package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Payment;
import com.rahbar.entity.PaymentInstallment;
import com.rahbar.entity.PaymentSchedule;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Installments between a sponsor and a student. They are built when a student is mapped to a sponsor, from:
 * <ul>
 *   <li>the course's number of semesters (2 semesters = 1 year),</li>
 *   <li>the Payment Config of the student's session year: the amount of each installment and how often one is due
 *       (every 3 or 4 months),</li>
 *   <li>the student's payment start date: the first installment is due on it, the next ones every 3 / 4 months.</li>
 * </ul>
 * E.g. 8 semesters, every 4 months: 4 years x 3 = 12 installments. An installment is Paid once linked to a Paid
 * payment, Due once its date has come and it isn't paid, else Not Due.
 * <p>
 * {@link #sync(Long)} rebuilds a student's unpaid installments whenever something they depend on changes (mapping,
 * course, session year, start date, Payment Config, study status); paid ones are never changed. When the student
 * moves to another sponsor, the unpaid installments move with them and the paid ones stay with the old sponsor.
 */
@Service
public class PaymentInstallmentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentInstallmentService.class);

    public static final String PAID = "Paid";
    public static final String DUE = "Due";
    public static final String NOT_DUE = "Not Due";

    private final PaymentInstallmentRepository installmentRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final PaymentRepository paymentRepository;
    /** syncAll / syncYear run one transaction per student (they call sync() on this bean, bypassing the proxy). */
    private final TransactionTemplate tx;

    public PaymentInstallmentService(PaymentInstallmentRepository installmentRepository,
                                     GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                     StudentInstitutionCourseRepository studentCourseRepository,
                                     PaymentScheduleRepository paymentScheduleRepository, PaymentRepository paymentRepository,
                                     org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.installmentRepository = installmentRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.userRepository = userRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.paymentRepository = paymentRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** What a student's installments are built from, or why they can't be built. */
    public record Plan(Long sponsorId, int count, LocalDate start, int frequencyMonths, BigDecimal amount, String problem) {
        boolean ok() { return problem == null; }
    }

    // ------------------------------------------------------------------------------------ building

    /** Works out the student's schedule; {@code problem} says what is missing when there is none. */
    public Plan plan(User student) {
        Long sponsorId = currentSponsor(student.getId());
        if (sponsorId == null) return problem("The student is not mapped to a sponsor.");
        if ("Inactive".equalsIgnoreCase(student.getStatus())) return problem("The student's account is inactive.");
        if (!StudyStatus.inProgramme(student.getStudyStatus())) {
            return problem("The student is " + StudyStatus.label(student.getStudyStatus()).toLowerCase(Locale.ROOT) + ".");
        }
        Map<String, Object> course = Rows.first(studentCourseRepository.findCourseInfo(student.getId()));
        int semesters = course != null && course.get("number_of_semesters") instanceof Number n ? n.intValue() : 0;
        if (semesters <= 0) return problem("No course (with its number of semesters) is assigned to the student.");
        if (student.getYear() == null) return problem("The student has no session year.");
        PaymentSchedule config = paymentScheduleRepository.findFirstByYearAndStatus(student.getYear(), 1).orElse(null);
        if (config == null || config.getAmount() == null) return problem("There is no Payment Config for " + student.getYear() + ".");
        if (student.getPaymentStartDate() == null) return problem("The student has no payment start date.");
        int frequency = config.getFrequencyMonths() == null || config.getFrequencyMonths() <= 0 ? 3 : config.getFrequencyMonths();
        // Each semester is 6 months: 8 semesters every 4 months -> 48 / 4 = 12 installments.
        int count = (int) Math.ceil(semesters * 6.0 / frequency);
        return new Plan(sponsorId, count, student.getPaymentStartDate(), frequency, config.getAmount(), null);
    }

    private static Plan problem(String text) {
        return new Plan(null, 0, null, 0, null, text);
    }

    /** The student's sponsor (users.id), or null when not mapped (or mapped to the "unassigned" grantor). */
    private Long currentSponsor(Long studentId) {
        Long sponsorId = grantorGranteeRepository.findFirstByGranteeId(studentId).map(GrantorGrantee::getGrantorId).orElse(null);
        if (sponsorId == null || sponsorId.equals(ServiceSupport.unassignedGrantorId(userRepository))) return null;
        return sponsorId;
    }

    /**
     * Brings the student's installments in line with their current mapping, course, session year, start date and
     * Payment Config. Paid installments are kept as they are; Paid payments not linked yet pay the earliest unpaid
     * installments (oldest payment first).
     */
    @Transactional
    public void sync(Long studentId) {
        User student = userRepository.findById(studentId).orElse(null);
        if (student == null || !Integer.valueOf(ServiceSupport.STUDENT_ROLE).equals(student.getRoleId())) return;

        List<PaymentInstallment> rows = new ArrayList<>(installmentRepository.findByGranteeIdOrderByInstallmentNoAsc(studentId));
        Map<Long, Payment> payments = new HashMap<>();
        paymentRepository.findByGranteeIdOrderByPaymentDateAsc(studentId).forEach(p -> payments.put(p.getPaymentId(), p));

        // A payment that is no longer Paid (edited or removed) no longer pays its installment.
        for (PaymentInstallment r : rows) {
            if (r.getPaymentId() != null && !isPaid(payments.get(r.getPaymentId()))) r.setPaymentId(null);
        }

        Plan plan = plan(student);
        List<PaymentInstallment> remove = new ArrayList<>();
        if (!plan.ok()) {
            rows.stream().filter(r -> r.getPaymentId() == null).forEach(remove::add);
        } else {
            Map<Integer, PaymentInstallment> byNo = new HashMap<>();
            rows.forEach(r -> byNo.put(r.getInstallmentNo(), r));
            for (int i = 1; i <= plan.count(); i++) {
                LocalDate due = plan.start().plusMonths((long) (i - 1) * plan.frequencyMonths());
                PaymentInstallment r = byNo.get(i);
                if (r == null) {
                    r = new PaymentInstallment();
                    r.setGranteeId(studentId);
                    r.setInstallmentNo(i);
                    rows.add(r);
                } else if (r.getPaymentId() != null) {
                    continue; // paid: keep its sponsor, date and amount
                }
                r.setGrantorId(plan.sponsorId());
                r.setDueDate(due);
                r.setAmount(plan.amount());
            }
            rows.stream().filter(r -> r.getPaymentId() == null && r.getInstallmentNo() > plan.count()).forEach(remove::add);
        }
        rows.removeAll(remove);
        installmentRepository.deleteAll(remove);
        installmentRepository.flush();

        // Paid payments that don't pay an installment yet pay the earliest unpaid ones.
        Set<Long> linked = new HashSet<>();
        rows.forEach(r -> { if (r.getPaymentId() != null) linked.add(r.getPaymentId()); });
        List<Payment> unlinked = payments.values().stream()
                .filter(PaymentInstallmentService::isPaid)
                .filter(p -> !linked.contains(p.getPaymentId()))
                .sorted(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Payment::getPaymentId))
                .toList();
        rows.sort(Comparator.comparing(PaymentInstallment::getInstallmentNo));
        Iterator<Payment> next = unlinked.iterator();
        for (PaymentInstallment r : rows) {
            if (!next.hasNext()) break;
            if (r.getPaymentId() == null) r.setPaymentId(next.next().getPaymentId());
        }
        installmentRepository.saveAll(rows);
    }

    /** Rebuilds every student who is mapped to a sponsor or already has installments. */
    public int syncAll() {
        Set<Long> ids = new LinkedHashSet<>(installmentRepository.findGranteeIds());
        grantorGranteeRepository.findAll().forEach(gg -> ids.add(gg.getGranteeId()));
        int failed = 0;
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(t -> sync(id));
            } catch (Exception e) {
                failed++;
                log.warn("Could not build the payment installments of student {}: {}", id, e.getMessage());
            }
        }
        if (failed > 0) log.warn("Payment installments: {} of {} students could not be built", failed, ids.size());
        return ids.size() - failed;
    }

    /** All students whose session year is this year (after its Payment Config changed). */
    public void syncYear(Integer year) {
        if (year == null) return;
        userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE).stream()
                .filter(u -> year.equals(u.getYear()))
                .forEach(u -> tx.executeWithoutResult(t -> sync(u.getId())));
    }

    /** At start-up (so existing mappings get their installments) and every night as a safety net. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Payment installments built for {} student(s)", syncAll());
    }

    @Scheduled(cron = "${app.installments.cron:0 30 2 * * *}")
    public void nightly() {
        syncAll();
    }

    // ------------------------------------------------------------------------------------ paying

    /**
     * After a payment was saved: it pays the given installment (or, without one, the student's earliest unpaid
     * installment) when its status is Paid. The installment must belong to the payment's student and be unpaid.
     */
    @Transactional
    public void afterPayment(Payment payment, Long installmentId) {
        if (installmentId != null && isPaid(payment)) {
            PaymentInstallment target = installmentRepository.findById(installmentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Installment not found."));
            if (!target.getGranteeId().equals(payment.getGranteeId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "That installment belongs to another student.");
            }
            Optional<PaymentInstallment> current = installmentRepository.findByPaymentId(payment.getPaymentId());
            if (current.isPresent() && !current.get().getInstallmentId().equals(installmentId)) {
                current.get().setPaymentId(null);
                installmentRepository.saveAndFlush(current.get());
            }
            if (target.getPaymentId() != null && !target.getPaymentId().equals(payment.getPaymentId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Installment #" + target.getInstallmentNo() + " is already paid.");
            }
            target.setPaymentId(payment.getPaymentId());
            installmentRepository.saveAndFlush(target);
        }
        sync(payment.getGranteeId());
    }

    /** Checks an installment can be paid by this sponsor before the payment is stored. */
    public void requireUnpaid(Long installmentId, Long studentId, Long sponsorId) {
        if (installmentId == null) return;
        PaymentInstallment i = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Installment not found."));
        if (!i.getGranteeId().equals(studentId) || (sponsorId != null && !i.getGrantorId().equals(sponsorId))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That installment is not between you and this student.");
        }
        if (i.getPaymentId() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "Installment #" + i.getInstallmentNo() + " is already paid.");
    }

    private static boolean isPaid(Payment p) {
        return p != null && "Paid".equalsIgnoreCase(p.getStatus());
    }

    // ------------------------------------------------------------------------------------ reading

    /** Paid / Due / Not Due. */
    public static String status(PaymentInstallment i, LocalDate today) {
        if (i.getPaymentId() != null) return PAID;
        return i.getDueDate().isAfter(today) ? NOT_DUE : DUE;
    }

    /** A student's installments (all sponsors) as rows, with the payment that paid each one. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forStudent(Long studentId) {
        return rows(installmentRepository.findByGranteeIdOrderByInstallmentNoAsc(studentId), false);
    }

    /** The installments between this sponsor and student (including ones this sponsor paid before a remap). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forSponsorAndStudent(Long sponsorId, Long studentId) {
        return rows(installmentRepository.findByGrantorIdAndGranteeIdOrderByInstallmentNoAsc(sponsorId, studentId), false);
    }

    /** Students with installments of this sponsor who are no longer mapped to them (e.g. moved to another sponsor). */
    @Transactional(readOnly = true)
    public List<Long> formerStudentIds(Long sponsorId, Set<Long> currentStudentIds) {
        return installmentRepository.findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(sponsorId).stream()
                .map(PaymentInstallment::getGranteeId).distinct()
                .filter(id -> !currentStudentIds.contains(id)).toList();
    }

    /** Why a student has no (or no more) installments, or null when the schedule is complete. */
    @Transactional(readOnly = true)
    public String problem(Long studentId) {
        User student = userRepository.findById(studentId).orElse(null);
        return student == null ? "Student not found." : plan(student).problem();
    }

    /**
     * Admin > Payment Records: installments of every student the user may see (chapter-scoped users: their
     * chapter's students), filtered by text (student / sponsor name or code) and status.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> search(String q, String status) {
        if (Access.rccScoped()) throw Access.forbidden("Payment records are not available for RCC-scoped roles.");
        Long chapterId = null;
        if (Access.chapterScoped()) {
            chapterId = Access.current().getUser().getChapterId();
            if (chapterId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "You are not linked to a chapter.");
        }
        List<Map<String, Object>> rows = rows(installmentRepository.findAllByOrderByGranteeIdAscInstallmentNoAsc(), true);
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Long chapter = chapterId;
        return rows.stream()
                .filter(r -> chapter == null || chapter.equals(r.get("student_chapter_id")))
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase((String) r.get("status")))
                .filter(r -> text.isEmpty() || java.util.stream.Stream.of("student_name", "student_code", "sponsor_name", "sponsor_code")
                        .anyMatch(k -> String.valueOf(r.get(k)).toLowerCase(Locale.ROOT).contains(text)))
                .peek(r -> r.remove("student_chapter_id"))
                .toList();
    }

    private List<Map<String, Object>> rows(List<PaymentInstallment> installments, boolean withNames) {
        LocalDate today = LocalDate.now();
        Set<Long> paymentIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        installments.forEach(i -> {
            if (i.getPaymentId() != null) paymentIds.add(i.getPaymentId());
            userIds.add(i.getGranteeId());
            userIds.add(i.getGrantorId());
        });
        Map<Long, Payment> payments = new HashMap<>();
        paymentRepository.findAllById(paymentIds).forEach(p -> payments.put(p.getPaymentId(), p));
        Map<Long, User> users = new HashMap<>();
        if (withNames) userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));

        List<Map<String, Object>> out = new ArrayList<>();
        for (PaymentInstallment i : installments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("installment_id", i.getInstallmentId());
            row.put("installment_no", i.getInstallmentNo());
            row.put("due_date", i.getDueDate());
            row.put("amount", i.getAmount());
            row.put("status", status(i, today));
            row.put("student_id", i.getGranteeId());
            row.put("sponsor_id", i.getGrantorId());
            if (withNames) {
                User s = users.get(i.getGranteeId()), sp = users.get(i.getGrantorId());
                row.put("student_code", s == null ? null : s.getUserId());
                row.put("student_name", s == null ? null : s.getName());
                row.put("student_chapter_id", s == null ? null : s.getChapterId());
                row.put("sponsor_code", sp == null ? null : sp.getUserId());
                row.put("sponsor_name", sp == null ? null : sp.getName());
            }
            Payment p = i.getPaymentId() == null ? null : payments.get(i.getPaymentId());
            row.put("payment_id", p == null ? null : p.getPaymentId());
            row.put("paid_amount", p == null ? null : p.getAmount());
            row.put("paid_date", p == null ? null : p.getPaymentDate());
            row.put("receipt_url", p == null ? null : p.getReceiptUrl());
            row.put("student_proof_url", p == null ? null : p.getStudentProofUrl());
            out.add(row);
        }
        return out;
    }

    /** For Payment Dues: per student, counts and dates worked out from the installments. */
    @Transactional(readOnly = true)
    public Map<Long, List<PaymentInstallment>> byStudent() {
        Map<Long, List<PaymentInstallment>> map = new HashMap<>();
        for (PaymentInstallment i : installmentRepository.findAllByOrderByGranteeIdAscInstallmentNoAsc()) {
            map.computeIfAbsent(i.getGranteeId(), k -> new ArrayList<>()).add(i);
        }
        return map;
    }
}
