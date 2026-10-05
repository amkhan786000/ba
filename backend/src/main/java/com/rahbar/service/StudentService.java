package com.rahbar.service;

import com.rahbar.entity.BankDetails;
import com.rahbar.entity.Payment;
import com.rahbar.entity.StudentProgress;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.rahbar.service.ServiceSupport.requireUser;

/** Student (role 6) screens: dashboard, payments, bank details and progress uploads. */
@Service
public class StudentService {

    static final String[] STUDENT_COLUMNS = {"user_id", "name", "email", "phone", "region", "status", "year"};

    private final UserRepository userRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final PaymentRepository paymentRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public StudentService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                          PaymentRepository paymentRepository, BankDetailsRepository bankDetailsRepository,
                          StudentInstitutionCourseRepository studentCourseRepository,
                          StudentProgressRepository studentProgressRepository,
                          FileStorageService fileStorageService, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.paymentRepository = paymentRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    public Map<String, Object> dashboard(String userId) {
        Map<String, Object> student = Rows.pick(requireUser(userRepository, userId, "Student not found"), STUDENT_COLUMNS);
        Map<String, Object> sponsor = grantorGranteeRepository.findFirstByGranteeId(userId)
                .flatMap(gg -> userRepository.findById(gg.getGrantorId()))
                .map(u -> Rows.pick(u, "user_id", "name", "email", "phone", "region"))
                .orElse(Map.of());
        return Map.of("student", student, "sponsor", sponsor);
    }

    public Map<String, Object> payments(String userId) {
        List<Map<String, Object>> payments = new ArrayList<>();
        for (Payment p : paymentRepository.findByGranteeIdOrderByPaymentDateAsc(userId)) {
            Map<String, Object> row = Rows.of(p);
            row.put("receiptLink", p.getReceiptUrl() != null ? "/uploads/" + p.getReceiptUrl() : null);
            payments.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payments", payments);
        result.put("bankDetails", bankDetails(userId));
        result.put("student", Rows.pick(requireUser(userRepository, userId, "Student not found"), STUDENT_COLUMNS));
        result.put("courseInfo", Rows.first(studentCourseRepository.findCourseInfo(userId)));
        return result;
    }

    /** The student's bank details row, or null. */
    public Map<String, Object> bankDetails(String userId) {
        return bankDetailsRepository.findFirstByUserId(userId).map(Rows::of).orElse(null);
    }

    public void saveBankDetails(String userId, Map<String, String> body) {
        BankDetails bank = bankDetailsRepository.findFirstByUserId(userId).orElseGet(() -> newBankDetails(userId));
        bank.setBankName(body.get("bankName"));
        bank.setAccountNumber(body.get("accountNumber"));
        bank.setIfscCode(body.get("ifscCode"));
        bank.setAccountName(body.get("accountName"));
        bankDetailsRepository.save(bank);
    }

    /** A new bank_details row with the next free id (bank_detail_id is assigned as MAX + 1). */
    BankDetails newBankDetails(String userId) {
        BankDetails bank = new BankDetails();
        bank.setBankDetailId(bankDetailsRepository.nextId());
        bank.setUserId(userId);
        return bank;
    }

    public void submitProgress(String userId, String marks, String year, String session, MultipartFile file) {
        Integer academicYear;
        try {
            academicYear = Integer.valueOf(year.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid year: " + year);
        }
        String ext = "";
        String original = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = userId + "_" + session.replace("/", "-") + "_" + timestamp + ext;
        fileStorageService.store(file, filename);

        StudentProgress progress = new StudentProgress();
        progress.setProgressId(studentProgressRepository.nextId());
        progress.setGranteeId(userId);
        progress.setMarks(marks);
        progress.setFilePath(filename);
        progress.setSession(session);
        progress.setYear(academicYear);
        progress.setReviewStatus("Pending");
        studentProgressRepository.save(progress);

        String studentName = userRepository.findById(userId).map(User::getName).orElse(userId);
        String summary = studentName + " uploaded marks (" + marks + ") for " + session + " " + academicYear + ".";
        grantorGranteeRepository.findFirstByGranteeId(userId).ifPresent(gg -> notificationService.notify(gg.getGrantorId(),
                "New progress report to review", summary,
                NotificationService.PROGRESS, "/sponsor/progress", false));
    }

    public List<Map<String, Object>> progressHistory(String userId) {
        return Rows.list(studentProgressRepository.findByGranteeIdOrderByCreatedAtDesc(userId));
    }

    public void uploadPaymentProof(String userId, Long paymentId, MultipartFile proofFile) {
        Payment payment = paymentRepository.findByPaymentIdAndGranteeId(paymentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found for this student."));
        String filename = fileStorageService.sanitizeFilename(
                "proof_" + paymentId + "_" + System.currentTimeMillis() + "_" + proofFile.getOriginalFilename());
        fileStorageService.store(proofFile, filename);
        payment.setStudentProofUrl(filename);
        paymentRepository.save(payment);
        String studentName = userRepository.findById(userId).map(User::getName).orElse(userId);
        notificationService.notify(payment.getGrantorId(), "Payment proof uploaded",
                studentName + " uploaded proof of receipt for a payment of " + payment.getAmount() + ".",
                NotificationService.PAYMENT, "/sponsor/payments", false);
    }

    /** Picks the safe user columns shown on student cards. */
    static Map<String, Object> studentCard(User u) {
        return Rows.pick(u, STUDENT_COLUMNS);
    }
}
