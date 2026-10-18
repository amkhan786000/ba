package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Payment;
import com.rahbar.entity.StudentProgress;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.ServiceSupport.*;

/** Sponsor (role 5) screens. Students are linked to a sponsor directly: grantor_grantees.grantor_id = sponsor's user_id. */
@Service
public class SponsorService {

    private final UserRepository userRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final com.rahbar.repository.SponsorMappingHistoryRepository historyRepository;
    private final PaymentInstallmentService installmentService;

    public SponsorService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                          PaymentRepository paymentRepository, PaymentScheduleRepository paymentScheduleRepository,
                          BankDetailsRepository bankDetailsRepository,
                          StudentInstitutionCourseRepository studentCourseRepository,
                          StudentProgressRepository studentProgressRepository,
                          FileStorageService fileStorageService, NotificationService notificationService,
                          PaymentInstallmentService installmentService,
                          com.rahbar.repository.SponsorMappingHistoryRepository historyRepository,
                          AlumniProfileRepository alumniRepository) {
        this.historyRepository = historyRepository;
        this.alumniRepository = alumniRepository;
        this.installmentService = installmentService;
        this.userRepository = userRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.paymentRepository = paymentRepository;
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    private final AlumniProfileRepository alumniRepository;

    /** A student with no progress report or payment for this long is shown as "no news lately". */
    static final int QUIET_DAYS = 120;

    /**
     * Sponsor dashboard, "My students & impact": each current student's study picture (course, institution, semester
     * now, expected completion, study status) and latest news (last progress report, last payment and whether its
     * spent proof came in, last activity); what past students do now (graduates who agreed to be contacted); and the
     * sponsor's impact. Only study information: no phone, address or family details.
     */
    public Map<String, Object> overview(Long sponsorId) {
        List<Payment> mine = paymentRepository.findByGrantorIdOrderByPaymentDateDesc(sponsorId).stream()
                .filter(p -> "Paid".equalsIgnoreCase(p.getStatus())).toList();
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> students = new ArrayList<>();
        Set<Long> supported = new LinkedHashSet<>();
        for (User u : userRepository.findGranteesOf(sponsorId)) {
            supported.add(u.getId());
            Map<String, Object> r = new LinkedHashMap<>(studyPicture(u, today));
            StudentProgress latest = studentProgressRepository.findByGranteeIdOrderByCreatedAtDesc(u.getId()).stream().findFirst().orElse(null);
            r.put("latest_progress", latest == null ? null : Map.of("session", String.valueOf(latest.getSession()),
                    "year", latest.getYear() == null ? "" : String.valueOf(latest.getYear()), "marks", latest.getMarks() == null ? "" : latest.getMarks(),
                    "review_status", latest.getReviewStatus() == null ? "" : latest.getReviewStatus(),
                    "uploaded_at", latest.getCreatedAt() == null ? "" : latest.getCreatedAt().toString()));
            Payment last = mine.stream().filter(p -> u.getId().equals(p.getGranteeId())).findFirst().orElse(null);
            r.put("last_payment", last == null ? null : Map.of("date", String.valueOf(last.getPaymentDate()),
                    "installment", installmentService.label(last.getPaymentId()), "spent_proof", last.getStudentProofUrl() != null && !last.getStudentProofUrl().isBlank()));
            LocalDateTime activity = latestOf(latest == null ? null : latest.getCreatedAt(), last == null ? null : last.getPaymentDate());
            r.put("last_activity", activity);
            r.put("quiet", activity == null || activity.toLocalDate().isBefore(today.minusDays(QUIET_DAYS)));
            students.add(r);
        }

        List<Map<String, Object>> pastNow = new ArrayList<>();
        for (Past p : past(sponsorId).values()) {
            User u = p.student();
            supported.add(u.getId());
            Map<String, Object> r = new LinkedHashMap<>(studyPicture(u, today));
            r.put("sponsored_from", p.from());
            r.put("sponsored_until", p.until());
            // What they do now: only graduates who agreed to be contacted share it.
            com.rahbar.entity.AlumniProfile a = StudyStatus.GRADUATED.equals(u.getStudyStatus())
                    ? alumniRepository.findById(u.getId()).filter(x -> Boolean.TRUE.equals(x.getConsentToContact())).orElse(null) : null;
            r.put("now", a == null ? null : Map.of(
                    "status", a.getCurrentStatus() == null ? "" : com.rahbar.entity.AlumniProfile.STATUSES.getOrDefault(a.getCurrentStatus(), a.getCurrentStatus()),
                    "organisation", a.getOrganisation() == null ? "" : a.getOrganisation(),
                    "role", a.getRoleTitle() == null ? "" : a.getRoleTitle(),
                    "city", a.getCity() == null ? "" : a.getCity()));
            pastNow.add(r);
        }

        LocalDateTime since = mine.stream().map(Payment::getPaymentDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        for (com.rahbar.entity.SponsorMappingHistory h : historyRepository.findByGrantorIdOrderByStartedAtDesc(sponsorId)) {
            if (h.getStartedAt() != null && (since == null || h.getStartedAt().isBefore(since))) since = h.getStartedAt();
        }
        long graduated = supported.stream().map(userRepository::findById).flatMap(Optional::stream)
                .filter(u -> StudyStatus.GRADUATED.equals(u.getStudyStatus())).count();
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("payments", mine.size()); // installments paid (amounts are never shown)
        impact.put("students_supported", supported.size());
        impact.put("studying_now", students.size());
        impact.put("graduated", graduated);
        impact.put("since_year", since == null ? null : since.getYear());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("impact", impact);
        body.put("students", students);
        body.put("past", pastNow);
        return body;
    }

    /** Course, institution, semester now and expected completion (semesters of 6 months from the course start). */
    private Map<String, Object> studyPicture(User u, LocalDate today) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId());
        r.put("user_id", u.getUserId());
        r.put("name", u.getName());
        r.put("study_status", StudyStatus.label(u.getStudyStatus()));
        Map<String, Object> c = Rows.first(studentCourseRepository.findCourseDetails(u.getId()));
        r.put("course", c == null ? null : c.get("course_name"));
        r.put("institution", c == null ? null : c.get("institution_name"));
        Integer semesters = c == null || c.get("number_of_semesters") == null ? null : ((Number) c.get("number_of_semesters")).intValue();
        LocalDate start = c == null || c.get("assigned_at") == null ? null : ((LocalDateTime) c.get("assigned_at")).toLocalDate();
        if (u.getPaymentStartDate() != null) start = u.getPaymentStartDate();
        r.put("semesters", semesters);
        r.put("course_start", start);
        r.put("expected_completion", start == null || semesters == null ? null : start.plusMonths(6L * semesters));
        Integer semesterNow = null;
        if (start != null && semesters != null && StudyStatus.STUDYING.equals(StudyStatus.of(u.getStudyStatus())) && !start.isAfter(today)) {
            semesterNow = (int) Math.min(semesters, java.time.temporal.ChronoUnit.MONTHS.between(start, today) / 6 + 1);
        }
        r.put("semester_now", semesterNow);
        return r;
    }

    private static LocalDateTime latestOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    public Map<String, Object> dashboard(Long sponsorId) {
        Map<String, Object> sponsor = Rows.pick(requireUser(userRepository, sponsorId, "Sponsor not found"),
                "id", "user_id", "name", "email", "phone", "chapter_id", "chapter_name", "status");

        List<Map<String, Object>> grantees = new ArrayList<>();
        for (GrantorGrantee gg : grantorGranteeRepository.findByGrantorId(sponsorId)) {
            Long granteeId = gg.getGranteeId();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("user", userRepository.findById(granteeId).map(StudentService::studentCard).orElse(null));
            entry.put("bankDetails", bankDetailsRepository.findFirstByUserId(granteeId).map(Rows::of).orElse(null));
            entry.put("latestPayment", paymentRepository.findFirstByGranteeIdOrderByCreatedAtDesc(granteeId).map(Rows::of).orElse(null));
            entry.put("paymentStatus", paymentStatus(sponsorId, granteeId));
            grantees.add(entry);
        }

        return Map.of("sponsor", sponsor, "grantees", grantees, "performanceByYear", performanceByYear(sponsorId),
                "pastStudents", pastStudents(sponsorId));
    }

    /** A student this sponsor sponsored before (not mapped to them now) and when that ended. */
    private record Past(User student, LocalDateTime from, LocalDateTime until) {}

    /**
     * Students this sponsor sponsored in the past: from the mapping history (with the period), plus older ones known
     * only from the installments / payments the sponsor made (their period ends at the last payment).
     */
    private Map<Long, Past> past(Long sponsorId) {
        Set<Long> current = new HashSet<>(granteeIds(sponsorId));
        Map<Long, Past> past = new LinkedHashMap<>();
        for (com.rahbar.entity.SponsorMappingHistory h : historyRepository.findByGrantorIdOrderByStartedAtDesc(sponsorId)) {
            if (h.getEndedAt() == null || current.contains(h.getGranteeId())) continue;
            userRepository.findById(h.getGranteeId()).ifPresent(u -> past.merge(u.getId(), new Past(u, h.getStartedAt(), h.getEndedAt()),
                    (a, b) -> new Past(u, a.from().isBefore(b.from()) ? a.from() : b.from(), a.until().isAfter(b.until()) ? a.until() : b.until())));
        }
        for (Payment p : paymentRepository.findByGrantorIdOrderByPaymentDateDesc(sponsorId)) {
            if (current.contains(p.getGranteeId()) || past.containsKey(p.getGranteeId()) || p.getPaymentDate() == null) continue;
            userRepository.findById(p.getGranteeId()).ifPresent(u -> past.put(u.getId(), new Past(u, null, p.getPaymentDate())));
        }
        return past;
    }

    /** Past students for the dashboard: who, when, and how many installments this sponsor paid for them. */
    private List<Map<String, Object>> pastStudents(Long sponsorId) {
        Map<Long, Long> paid = new HashMap<>();
        for (Payment p : paymentRepository.findByGrantorIdOrderByPaymentDateDesc(sponsorId)) {
            if ("Paid".equalsIgnoreCase(p.getStatus())) paid.merge(p.getGranteeId(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Past p : past(sponsorId).values()) {
            Map<String, Object> row = new LinkedHashMap<>(StudentService.studentCard(p.student()));
            row.put("sponsored_from", p.from());
            row.put("sponsored_until", p.until());
            row.put("installments_paid", paid.getOrDefault(p.student().getId(), 0L));
            rows.add(row);
        }
        return rows;
    }

    /** From the installments between this sponsor and the student: Pending (none yet), Completed, Overdue or On Schedule. */
    private String paymentStatus(Long sponsorId, Long granteeId) {
        List<Map<String, Object>> rows = installmentService.forSponsorAndStudent(sponsorId, granteeId);
        if (rows.isEmpty()) return "Pending";
        if (rows.stream().allMatch(r -> PaymentInstallmentService.PAID.equals(r.get("status")))) return "Completed";
        if (rows.stream().anyMatch(r -> PaymentInstallmentService.OVERDUE.equals(r.get("status")))) return "Overdue";
        return rows.stream().anyMatch(r -> PaymentInstallmentService.DUE.equals(r.get("status"))) ? "Due" : "On Schedule";
    }

    /** Average marks per academic year of this sponsor's students: [{label: "Year N", value}]. */
    private List<Map<String, Object>> performanceByYear(Long sponsorId) {
        List<Long> granteeIds = granteeIds(sponsorId);
        if (granteeIds.isEmpty()) return List.of();
        Map<Integer, List<Double>> byYear = new TreeMap<>();
        for (StudentProgress sp : studentProgressRepository.findByGranteeIdInOrderByCreatedAtDesc(granteeIds)) {
            if (sp.getYear() == null) continue;
            List<Double> list = byYear.computeIfAbsent(sp.getYear(), y -> new ArrayList<>());
            Double m = marks(sp.getMarks());
            if (m != null) list.add(m);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byYear.forEach((year, values) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", "Year " + year);
            row.put("value", values.isEmpty() ? null : BigDecimal.valueOf(
                    values.stream().mapToDouble(Double::doubleValue).average().orElse(0)).setScale(1, RoundingMode.HALF_UP));
            rows.add(row);
        });
        return rows;
    }

    public Map<String, Object> payments(Long sponsorId, Long selectedGranteeId) {
        List<Map<String, Object>> paymentDetails = new ArrayList<>();
        Map<String, Object> studentDataMap = new LinkedHashMap<>();
        for (User student : userRepository.findGranteesOf(sponsorId)) {
            Long sId = student.getId();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("grantee", StudentService.studentCard(student));
            detail.put("bankDetails", bankDetailsRepository.findFirstByUserId(sId).map(Rows::of).orElse(null));
            detail.put("payments", Rows.list(paymentRepository.findByGranteeIdAndStatusOrderByPaymentDateDesc(sId, "Paid")));
            detail.put("courseInfo", Rows.first(studentCourseRepository.findCourseInfo(sId)));
            detail.put("installments", installmentService.forSponsorAndStudent(sponsorId, sId));
            detail.put("installmentProblem", installmentService.problem(sId));
            detail.put("former", false);
            paymentDetails.add(detail);
            studentDataMap.put(String.valueOf(sId), detail);
        }

        // Students who moved to another sponsor: only the installments this sponsor paid (read-only, no bank details).
        Set<Long> current = new HashSet<>();
        paymentDetails.forEach(d -> current.add((Long) ((Map<?, ?>) d.get("grantee")).get("id")));
        for (Long formerId : installmentService.formerStudentIds(sponsorId, current)) {
            userRepository.findById(formerId).ifPresent(student -> {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("grantee", StudentService.studentCard(student));
                detail.put("bankDetails", null);
                detail.put("payments", List.of());
                detail.put("courseInfo", null);
                detail.put("installments", installmentService.forSponsorAndStudent(sponsorId, formerId));
                detail.put("installmentProblem", null);
                detail.put("former", true);
                paymentDetails.add(detail);
                studentDataMap.put(String.valueOf(formerId), detail);
            });
        }

        List<Payment> recent = paymentRepository.findTop5ByGrantorIdAndStatusOrderByPaymentDateDesc(sponsorId, "Paid");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentDetails", paymentDetails);
        result.put("pastPayments", withGranteeName(recent));
        result.put("studentDataMap", studentDataMap);
        result.put("selectedGranteeId", selectedGranteeId);
        return result;
    }

    /** A sponsor can only record payments for students mapped to them. */
    public void recordPayment(Long sponsorId, Long granteeId, BigDecimal amount, String paymentDate, MultipartFile receipt,
                              Long installmentId) {
        if (!grantorGranteeRepository.existsByGranteeIdAndGrantorId(granteeId, sponsorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Error: This student is not assigned to you.");
        }
        installmentService.requireUnpaid(installmentId, granteeId, sponsorId);
        String filename = "sponsor_pay_" + granteeId + "_" + System.currentTimeMillis() + "_"
                + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        Payment payment = new Payment();
        payment.setGrantorId(sponsorId);
        payment.setGranteeId(granteeId);
        payment.setAmount(amount);
        payment.setPaymentDate(parseDateTime(paymentDate));
        payment.setReceiptUrl(fileStorageService.store(receipt, filename));
        payment.setStatus("Paid");
        paymentRepository.saveAndFlush(payment);
        installmentService.afterPayment(payment, installmentId); // without one: the earliest unpaid installment
        User student = userRepository.findById(granteeId).orElse(null);
        String sponsorName = userRepository.findById(sponsorId).map(sp -> com.rahbar.security.SponsorPrivacy.forStudent(sp, student))
                .orElse("Your sponsor");
        notificationService.notify(granteeId, "Payment received",
                sponsorName + " paid " + installmentService.label(payment.getPaymentId()) + " for you. Please upload your proof of receipt.",
                NotificationService.PAYMENT, "/student/payments", EmailType.PAYMENT_RECEIVED,
                ServiceSupport.vars("sponsor_name", sponsorName, "installment", installmentService.number(payment.getPaymentId())));
    }

    /**
     * Progress reports of the sponsor's current students, and of past students up to the day they moved to another
     * sponsor (marked past = true).
     */
    public List<Map<String, Object>> studentProgress(Long sponsorId) {
        List<Long> ids = new ArrayList<>(granteeIds(sponsorId));
        Map<Long, Past> past = past(sponsorId);
        ids.addAll(past.keySet());
        if (ids.isEmpty()) return List.of();
        List<StudentProgress> progress = studentProgressRepository.findByGranteeIdInOrderByCreatedAtDesc(ids);
        Map<Long, User> students = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> students.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StudentProgress sp : progress) {
            User student = students.get(sp.getGranteeId());
            if (student == null) continue;
            Past p = past.get(sp.getGranteeId());
            if (p != null && sp.getCreatedAt() != null && sp.getCreatedAt().isAfter(p.until())) continue; // after they moved on
            Map<String, Object> row = Rows.of(sp);
            row.put("grantee_name", student.getName());
            row.put("grantee_code", student.getUserId());
            row.put("past", p != null);
            rows.add(row);
        }
        return rows;
    }

    private List<Long> granteeIds(Long sponsorId) {
        return grantorGranteeRepository.findByGrantorId(sponsorId).stream().map(GrantorGrantee::getGranteeId).toList();
    }

    /** Payment rows with the student's name as grantee_name (payments of unknown students are dropped). */
    private List<Map<String, Object>> withGranteeName(List<Payment> payments) {
        Map<Long, String> names = userNames(userRepository, payments.stream().map(Payment::getGranteeId).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment p : payments) {
            if (!names.containsKey(p.getGranteeId())) continue;
            Map<String, Object> row = Rows.of(p);
            row.put("grantee_name", names.get(p.getGranteeId()));
            row.put("installment", installmentService.label(p.getPaymentId()));
            rows.add(row);
        }
        return rows;
    }
}
