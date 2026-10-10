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
    private final PaymentInstallmentService installmentService;

    public SponsorService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                          PaymentRepository paymentRepository, PaymentScheduleRepository paymentScheduleRepository,
                          BankDetailsRepository bankDetailsRepository,
                          StudentInstitutionCourseRepository studentCourseRepository,
                          StudentProgressRepository studentProgressRepository,
                          FileStorageService fileStorageService, NotificationService notificationService,
                          PaymentInstallmentService installmentService) {
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

        return Map.of("sponsor", sponsor, "grantees", grantees, "performanceByYear", performanceByYear(sponsorId));
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
            Object annualAmount = student.getYear() == null ? 0 : paymentScheduleRepository
                    .findFirstByYearAndStatus(student.getYear(), 1).map(s -> (Object) s.getAmount()).orElse(0);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("grantee", StudentService.studentCard(student));
            detail.put("bankDetails", bankDetailsRepository.findFirstByUserId(sId).map(Rows::of).orElse(null));
            detail.put("payments", Rows.list(paymentRepository.findByGranteeIdAndStatusOrderByPaymentDateDesc(sId, "Paid")));
            detail.put("courseInfo", Rows.first(studentCourseRepository.findCourseInfo(sId)));
            detail.put("annualScheduleAmount", annualAmount);
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
                detail.put("annualScheduleAmount", null);
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
        String sponsorName = userRepository.findById(sponsorId).map(User::getName).orElse("Your sponsor");
        notificationService.notify(granteeId, "Payment received",
                sponsorName + " recorded a payment of " + amount + " for you. Please upload your proof of receipt.",
                NotificationService.PAYMENT, "/student/payments",
                EmailType.PAYMENT_RECEIVED, ServiceSupport.vars("sponsor_name", sponsorName, "amount", amount));
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
