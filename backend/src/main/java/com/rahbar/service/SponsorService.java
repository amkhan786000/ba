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

    public SponsorService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                          PaymentRepository paymentRepository, PaymentScheduleRepository paymentScheduleRepository,
                          BankDetailsRepository bankDetailsRepository,
                          StudentInstitutionCourseRepository studentCourseRepository,
                          StudentProgressRepository studentProgressRepository,
                          FileStorageService fileStorageService, NotificationService notificationService) {
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
            entry.put("paymentStatus", paymentStatus(granteeId));
            grantees.add(entry);
        }

        return Map.of("sponsor", sponsor, "grantees", grantees, "performanceByYear", performanceByYear(sponsorId));
    }

    /**
     * Flask showed "On Schedule" for anyone with a course; this checks quarterly installments actually due vs paid
     * (same schedule as the payments page: (semesters / 2) * 4 installments, one every 3 months).
     */
    private String paymentStatus(Long granteeId) {
        Map<String, Object> courseInfo = Rows.first(studentCourseRepository.findCourseInfo(granteeId));
        if (courseInfo == null || courseInfo.get("assigned_at") == null) return "Pending";
        LocalDate start = ((LocalDateTime) courseInfo.get("assigned_at")).toLocalDate();
        Object sem = courseInfo.get("number_of_semesters");
        int semesters = sem == null ? 0 : ((Number) sem).intValue();
        int total = (int) Math.floor(semesters / 2.0 * 4);
        int dueSoFar = 0;
        for (int i = 1; i <= total; i++) {
            if (!start.plusMonths(3L * i).isAfter(LocalDate.now())) dueSoFar++;
        }
        long paidCount = paymentRepository.countByGranteeIdAndStatus(granteeId, "Paid");
        if (total > 0 && paidCount >= total) return "Completed";
        return paidCount >= dueSoFar ? "On Schedule" : "Overdue";
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
            Object annualAmount = student.getYear() == null ? 0 : paymentScheduleRepository
                    .findFirstByYearAndStatus(student.getYear(), 1).map(s -> (Object) s.getAmount()).orElse(0);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("grantee", StudentService.studentCard(student));
            detail.put("bankDetails", bankDetailsRepository.findFirstByUserId(sId).map(Rows::of).orElse(null));
            detail.put("payments", Rows.list(paymentRepository.findByGranteeIdAndStatusOrderByPaymentDateDesc(sId, "Paid")));
            detail.put("courseInfo", Rows.first(studentCourseRepository.findCourseInfo(sId)));
            detail.put("annualScheduleAmount", annualAmount);
            paymentDetails.add(detail);
            studentDataMap.put(String.valueOf(sId), detail);
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
    public void recordPayment(Long sponsorId, Long granteeId, BigDecimal amount, String paymentDate, MultipartFile receipt) {
        if (!grantorGranteeRepository.existsByGranteeIdAndGrantorId(granteeId, sponsorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Error: This student is not assigned to you.");
        }
        String filename = "sponsor_pay_" + granteeId + "_" + System.currentTimeMillis() + "_"
                + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        Payment payment = new Payment();
        payment.setGrantorId(sponsorId);
        payment.setGranteeId(granteeId);
        payment.setAmount(amount);
        payment.setPaymentDate(parseDateTime(paymentDate));
        payment.setReceiptUrl(fileStorageService.store(receipt, filename));
        payment.setStatus("Paid");
        paymentRepository.save(payment);
        String sponsorName = userRepository.findById(sponsorId).map(User::getName).orElse("Your sponsor");
        notificationService.notify(granteeId, "Payment received",
                sponsorName + " recorded a payment of " + amount + " for you. Please upload your proof of receipt.",
                NotificationService.PAYMENT, "/student/payments", true);
    }

    public List<Map<String, Object>> studentProgress(Long sponsorId) {
        List<Long> ids = granteeIds(sponsorId);
        if (ids.isEmpty()) return List.of();
        List<StudentProgress> progress = studentProgressRepository.findByGranteeIdInOrderByCreatedAtDesc(ids);
        Map<Long, User> students = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> students.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StudentProgress sp : progress) {
            User student = students.get(sp.getGranteeId());
            if (student == null) continue;
            Map<String, Object> row = Rows.of(sp);
            row.put("grantee_name", student.getName());
            row.put("grantee_code", student.getUserId());
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
            rows.add(row);
        }
        return rows;
    }
}
