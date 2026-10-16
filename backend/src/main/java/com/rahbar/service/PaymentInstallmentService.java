package com.rahbar.service;

import com.rahbar.util.UserRoles;
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
import com.rahbar.dto.InstallmentPlan;

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
    /** Chapter / RCC-scoped users only see their own students. */
    private final com.rahbar.security.StudentScope studentScope;


    private static final Logger log = LoggerFactory.getLogger(PaymentInstallmentService.class);

    public static final String PAID = "Paid";
    public static final String DUE = "Due";
    public static final String OVERDUE = "Overdue";
    /** Default "show as due" lead time when a Payment Config has none. */
    public static final int DEFAULT_NOTICE_DAYS = 30;
    public static final String NOT_DUE = "Not Due";

    /** College types (BA Details > College type, and Payment Config). */
    public static final String GOVT = "GOVT";
    public static final String PRIVATE = "PRIVATE";
    public static final String ANY = "ANY";
    public static final Set<String> COLLEGE_TYPES = Set.of(GOVT, PRIVATE, ANY);

    /** The Payment Config chosen for a student, or why there is none. */
    public record ConfigChoice(PaymentSchedule config, String problem) {}

    /**
     * Which of a session year's (active) Payment Configs a student gets:
     * <ul>
     *   <li>a Govt / Private student: their type's config, else the year's "Any" config;</li>
     *   <li>"Doesn't matter" (or not set yet): the "Any" config; when the year has only one config, that one; when it
     *       has separate Govt and Private configs and no "Any" one, none (the college type must be chosen).</li>
     * </ul>
     */
    public static ConfigChoice choose(List<PaymentSchedule> yearConfigs, Integer year, String collegeType) {
        List<PaymentSchedule> active = yearConfigs.stream().filter(c -> c.getAmount() != null).toList();
        if (active.isEmpty()) return new ConfigChoice(null, "There is no Payment Config for " + year + ".");
        PaymentSchedule any = active.stream().filter(c -> ANY.equals(type(c.getCollegeType()))).findFirst().orElse(null);
        String type = type(collegeType);
        if (GOVT.equals(type) || PRIVATE.equals(type)) {
            PaymentSchedule own = active.stream().filter(c -> type.equals(type(c.getCollegeType()))).findFirst().orElse(any);
            return own != null ? new ConfigChoice(own, null)
                    : new ConfigChoice(null, "There is no Payment Config for " + year + " for " + collegeLabel(type).toLowerCase(Locale.ROOT) + " colleges.");
        }
        if (any != null) return new ConfigChoice(any, null);
        if (active.size() == 1) return new ConfigChoice(active.get(0), null);
        return new ConfigChoice(null, "Set the student's college type (Govt or Private) in BA Details: " + year
                + " has separate Payment Configs for government and private colleges.");
    }

    /** GOVT / PRIVATE / ANY; null and anything else count as ANY. */
    public static String type(String collegeType) {
        String t = collegeType == null ? "" : collegeType.trim().toUpperCase(Locale.ROOT);
        return GOVT.equals(t) || PRIVATE.equals(t) ? t : ANY;
    }

    public static String collegeLabel(String collegeType) {
        return switch (type(collegeType)) { case GOVT -> "Govt"; case PRIVATE -> "Private"; default -> "Any college"; };
    }

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
                                     org.springframework.transaction.PlatformTransactionManager transactionManager,
                                     com.rahbar.security.StudentScope studentScope) {
        this.studentScope = studentScope;
        this.installmentRepository = installmentRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.userRepository = userRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.paymentRepository = paymentRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------------------------ building

    /** Works out the student's schedule; {@code problem} says what is missing when there is none. */
    public InstallmentPlan plan(User student) {
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
        ConfigChoice choice = choose(paymentScheduleRepository.findByYearAndStatus(student.getYear(), 1), student.getYear(), student.getCollegeType());
        if (choice.config() == null) return problem(choice.problem());
        PaymentSchedule config = choice.config();
        if (student.getPaymentStartDate() == null) return problem("The student has no payment start date.");
        int frequency = config.getFrequencyMonths() == null || config.getFrequencyMonths() <= 0 ? 3 : config.getFrequencyMonths();
        // Each semester is 6 months: 8 semesters every 4 months -> 48 / 4 = 12 installments.
        int count = (int) Math.ceil(semesters * 6.0 / frequency);
        return new InstallmentPlan(sponsorId, count, student.getPaymentStartDate(), frequency, config.getAmount(), null);
    }

    private static InstallmentPlan problem(String text) {
        return new InstallmentPlan(null, 0, null, 0, null, text);
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
        if (student == null || !UserRoles.has(student, ServiceSupport.STUDENT_ROLE, userRepository)) return;

        List<PaymentInstallment> rows = new ArrayList<>(installmentRepository.findByGranteeIdOrderByInstallmentNoAsc(studentId));
        Map<Long, Payment> payments = new HashMap<>();
        paymentRepository.findByGranteeIdOrderByPaymentDateAsc(studentId).forEach(p -> payments.put(p.getPaymentId(), p));

        // A payment that is no longer Paid (edited or removed) no longer pays its installment.
        for (PaymentInstallment r : rows) {
            if (r.getPaymentId() != null && !isPaid(payments.get(r.getPaymentId()))) r.setPaymentId(null);
        }

        InstallmentPlan plan = plan(student);
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

    /**
     * Paid; Overdue once the due date has passed; Due from {@code noticeDays} before the due date until the due
     * date itself; otherwise Not Due.
     */
    public static String status(PaymentInstallment i, LocalDate today, int noticeDays) {
        if (i.getPaymentId() != null) return PAID;
        if (i.getDueDate().isBefore(today)) return OVERDUE;
        return i.getDueDate().isAfter(today.plusDays(noticeDays)) ? NOT_DUE : DUE;
    }

    /** "Show as due" lead time (days) per student, from their Payment Config (session year and college type). */
    public Map<Long, Integer> noticeDays(Collection<Long> studentIds) {
        Map<Integer, List<PaymentSchedule>> byYear = new HashMap<>();
        paymentScheduleRepository.findAll().stream().filter(ps -> Integer.valueOf(1).equals(ps.getStatus()))
                .forEach(ps -> byYear.computeIfAbsent(ps.getYear(), y -> new ArrayList<>()).add(ps));
        Map<Long, Integer> out = new HashMap<>();
        userRepository.findAllById(studentIds).forEach(u -> {
            PaymentSchedule c = u.getYear() == null ? null : choose(byYear.getOrDefault(u.getYear(), List.of()), u.getYear(), u.getCollegeType()).config();
            out.put(u.getId(), c == null || c.getDueNoticeDays() == null ? DEFAULT_NOTICE_DAYS : c.getDueNoticeDays());
        });
        return out;
    }

    /**
     * How a payment is named on screens and in emails: "installment 3" (amounts are never shown), or "a payment"
     * when it doesn't pay an installment.
     */
    @Transactional(readOnly = true)
    public String label(Long paymentId) {
        return paymentId == null ? "a payment" : installmentRepository.findByPaymentId(paymentId)
                .map(i -> "installment " + i.getInstallmentNo()).orElse("a payment");
    }

    /** Just the number for an email's {{installment}} ("3"), or "" when the payment pays none. */
    @Transactional(readOnly = true)
    public String number(Long paymentId) {
        return paymentId == null ? "" : installmentRepository.findByPaymentId(paymentId)
                .map(i -> String.valueOf(i.getInstallmentNo())).orElse("");
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

    /**
     * Sponsor > Fee schedule: each student mapped to the sponsor with how many installments they have (paid / due)
     * and, when the schedule can't be built, what is missing (e.g. no payment start date).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sponsorOverview(Long sponsorId) {
        studentScope.requireSponsor(sponsorId);
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User s : userRepository.findGranteesOf(sponsorId)) {
            if (!scope.includes(s)) continue;
            List<PaymentInstallment> mine = installmentRepository.findByGranteeIdOrderByInstallmentNoAsc(s.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_id", s.getId());
            row.put("student_code", s.getUserId());
            row.put("student_name", s.getName());
            row.put("session_year", s.getYear());
            row.put("payment_start_date", s.getPaymentStartDate());
            row.put("installments", mine.size());
            row.put("paid", mine.stream().filter(i -> i.getPaymentId() != null).count());
            int notice = noticeDays(List.of(s.getId())).getOrDefault(s.getId(), DEFAULT_NOTICE_DAYS);
            row.put("due", mine.stream().filter(i -> DUE.equals(status(i, today, notice))).count());
            row.put("overdue", mine.stream().filter(i -> OVERDUE.equals(status(i, today, notice))).count());
            row.put("problem", plan(s).problem());
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("student_name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Builds (or brings up to date) the installments of every student mapped to the sponsor; paid ones never change. */
    public Map<String, Object> generateForSponsor(Long sponsorId) {
        studentScope.requireSponsor(sponsorId);
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        List<User> students = userRepository.findGranteesOf(sponsorId).stream().filter(scope::includes).toList();
        int before = 0, after = 0, ready = 0;
        for (User s : students) {
            before += installmentRepository.findByGranteeIdOrderByInstallmentNoAsc(s.getId()).size();
            tx.executeWithoutResult(t -> sync(s.getId()));
            after += installmentRepository.findByGranteeIdOrderByInstallmentNoAsc(s.getId()).size();
        }
        List<Map<String, Object>> rows = sponsorOverview(sponsorId);
        for (Map<String, Object> r : rows) if (r.get("problem") == null) ready++;
        int blocked = rows.size() - ready;
        String message = students.isEmpty() ? "No students are mapped to this sponsor."
                : "Fee schedules are up to date for " + ready + " of " + rows.size() + " student(s)"
                + (after > before ? " (" + (after - before) + " installment(s) created)" : "")
                + (blocked > 0 ? ". " + blocked + " can't be built yet; see what is missing below." : ".");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("rows", rows);
        return body;
    }

    /** Why a student has no (or no more) installments, or null when the schedule is complete. */
    @Transactional(readOnly = true)
    public String problem(Long studentId) {
        User student = userRepository.findById(studentId).orElse(null);
        return student == null ? "Student not found." : plan(student).problem();
    }

    /**
     * Admin > Payment Records: installments of every student the user may see (chapter-scoped users: their
     * chapter's students), filtered by text (student / sponsor name or code) and status, one page at a time, with
     * the number of installments in each status for the cards above the table. Amounts stay in the database but are
     * never sent to a screen: everyone sees installments (installment 2 paid, installment 3 due).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> search(String q, String status, Integer page, Integer size) {
        com.rahbar.security.StudentScope.Scope scope = studentScope.current(); // chapter / RCC-scoped: own students only
        Map<Long, User> scopeUsers = scope.all() ? Map.of() : studentScope.usersById();
        List<PaymentInstallment> all = installmentRepository.findAllByOrderByGranteeIdAscInstallmentNoAsc().stream()
                .filter(i -> scope.includesId(i.getGranteeId(), scopeUsers)).toList();
        LocalDate today = LocalDate.now();
        Map<Long, Integer> notice = noticeDays(all.stream().map(PaymentInstallment::getGranteeId).collect(java.util.stream.Collectors.toSet()));
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(all.stream().flatMap(i -> java.util.stream.Stream.of(i.getGranteeId(), i.getGrantorId()))
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())).forEach(u -> users.put(u.getId(), u));

        // Cards above the table: every status of the installments the user may see (search and status filter aside).
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (String st : List.of(OVERDUE, DUE, NOT_DUE, PAID)) counts.put(st, new long[1]);
        List<PaymentInstallment> filtered = new ArrayList<>();
        for (PaymentInstallment i : all) {
            String st = status(i, today, notice.getOrDefault(i.getGranteeId(), DEFAULT_NOTICE_DAYS));
            counts.computeIfAbsent(st, k -> new long[1])[0]++;
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(st)) continue;
            User s = users.get(i.getGranteeId()), sp = users.get(i.getGrantorId());
            if (!com.rahbar.util.Paging.matches(q, s == null ? null : s.getName(), s == null ? null : s.getUserId(),
                    sp == null ? null : sp.getName(), sp == null ? null : sp.getUserId())) continue;
            filtered.add(i);
        }
        Map<String, Object> body = com.rahbar.util.Paging.of(filtered, page, size, slice -> {
            List<Map<String, Object>> rows = rows(slice, true);
            rows.forEach(r -> r.remove("student_chapter_id"));
            return rows;
        });
        List<Map<String, Object>> summary = new ArrayList<>();
        counts.forEach((st, c) -> summary.add(Map.of("status", st, "count", c[0])));
        body.put("summary", summary);
        return body;
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
        Map<Long, Integer> notice = noticeDays(installments.stream().map(PaymentInstallment::getGranteeId).collect(java.util.stream.Collectors.toSet()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (PaymentInstallment i : installments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("installment_id", i.getInstallmentId());
            row.put("installment_no", i.getInstallmentNo());
            row.put("due_date", i.getDueDate());
            row.put("status", status(i, today, notice.getOrDefault(i.getGranteeId(), DEFAULT_NOTICE_DAYS)));
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
