package com.rahbar.controller;

import com.rahbar.exception.ApiException;
import com.rahbar.security.AuthUtil;
import com.rahbar.service.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Mirrors routes/student.py (role_id 6 = beneficiary / grantee). */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('6')")
public class StudentController {

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorageService;

    public StudentController(JdbcTemplate jdbc, FileStorageService fileStorageService) {
        this.jdbc = jdbc;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        String userId = AuthUtil.currentUser().getUserId();
        Map<String, Object> student = jdbc.queryForMap(
                "SELECT user_id, name, email, phone, region, status, year FROM users WHERE user_id = ?", userId);
        Map<String, Object> sponsor = first(jdbc.queryForList("""
            SELECT u.user_id, u.name, u.email, u.phone, u.region, sr.reference_id FROM grantor_grantees gg
            JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id
            JOIN users u ON sr.user_id = u.user_id WHERE gg.grantee_id = ?
            """, userId));
        return Map.of("student", student, "sponsor", sponsor == null ? Map.of() : sponsor);
    }

    @GetMapping("/payments")
    public Map<String, Object> payments() {
        String userId = AuthUtil.currentUser().getUserId();
        List<Map<String, Object>> rawPayments = jdbc.queryForList(
                "SELECT * FROM payments WHERE grantee_id = ? ORDER BY payment_date ASC", userId);
        List<Map<String, Object>> payments = new ArrayList<>();
        for (Map<String, Object> p : rawPayments) {
            Map<String, Object> copy = new LinkedHashMap<>(p);
            Object receiptUrl = p.get("receipt_url");
            copy.put("receiptLink", receiptUrl != null ? "/uploads/" + receiptUrl : null);
            payments.add(copy);
        }
        Map<String, Object> student = jdbc.queryForMap(
                "SELECT user_id, name, email, phone, region, status, year FROM users WHERE user_id = ?", userId);
        Map<String, Object> courseInfo = first(jdbc.queryForList("""
            SELECT sic.assigned_at, c.number_of_semesters, c.fees_per_semester FROM student_institution_courses sic
            JOIN courses c ON sic.course_id = c.course_id WHERE sic.user_id = ?
            """, userId));
        Map<String, Object> bankDetails = first(jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", userId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payments", payments);
        result.put("bankDetails", bankDetails);
        result.put("student", student);
        result.put("courseInfo", courseInfo);
        return result;
    }

    @GetMapping("/bank-details")
    public Map<String, Object> getBankDetails() {
        Map<String, Object> bank = first(jdbc.queryForList(
                "SELECT * FROM bank_details WHERE user_id = ?", AuthUtil.currentUser().getUserId()));
        return bank == null ? Map.of() : bank;
    }

    @PostMapping("/bank-details")
    public Map<String, Object> saveBankDetails(@RequestBody Map<String, String> body) {
        String userId = AuthUtil.currentUser().getUserId();
        boolean exists = !jdbc.queryForList("SELECT bank_detail_id FROM bank_details WHERE user_id = ?", userId).isEmpty();
        if (exists) {
            jdbc.update("UPDATE bank_details SET bank_name=?, account_number=?, ifsc_code=?, account_name=? WHERE user_id=?",
                    body.get("bankName"), body.get("accountNumber"), body.get("ifscCode"), body.get("accountName"), userId);
        } else {
            // bank_detail_id isn't auto-generated (the admin code assigns MAX+1 the same way)
            Long nextId = jdbc.queryForObject("SELECT COALESCE(MAX(bank_detail_id),0)+1 FROM bank_details", Long.class);
            jdbc.update("INSERT INTO bank_details (bank_detail_id, user_id, bank_name, account_number, ifsc_code, account_name) VALUES (?,?,?,?,?,?)",
                    nextId, userId, body.get("bankName"), body.get("accountNumber"), body.get("ifscCode"), body.get("accountName"));
        }
        return Map.of("success", true, "message", "Bank details updated successfully!");
    }

    @PostMapping(value = "/progress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> submitProgress(@RequestParam String marks,
                                                @RequestParam String year,
                                                @RequestParam String session,
                                                @RequestParam MultipartFile file) {
        String userId = AuthUtil.currentUser().getUserId();
        Long newId = jdbc.queryForObject("SELECT COALESCE(MAX(progress_id),0)+1 FROM student_progress", Long.class);
        String ext = "";
        String original = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot);
        String cleanSession = session.replace("/", "-");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = userId + "_" + cleanSession + "_" + timestamp + ext;
        fileStorageService.store(file, filename);

        jdbc.update("""
            INSERT INTO student_progress (progress_id, grantee_id, marks, file_path, created_at, updated_at, session, year, updated_by)
            VALUES (?, ?, ?, ?, NOW(), NOW(), ?, ?, ?)
            """, newId, userId, marks, filename, session, year, userId);
        return Map.of("message", "Progress submitted successfully!");
    }

    @GetMapping("/progress")
    public List<Map<String, Object>> progressHistory() {
        return jdbc.queryForList(
                "SELECT * FROM student_progress WHERE grantee_id = ? ORDER BY created_at DESC",
                AuthUtil.currentUser().getUserId());
    }

    @PostMapping(value = "/payments/{paymentId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadPaymentProof(@PathVariable Long paymentId, @RequestParam MultipartFile proofFile) {
        String userId = AuthUtil.currentUser().getUserId();
        String filename = fileStorageService.sanitizeFilename("proof_" + paymentId + "_" + System.currentTimeMillis() + "_" + proofFile.getOriginalFilename());
        fileStorageService.store(proofFile, filename);
        int updated = jdbc.update(
                "UPDATE payments SET student_proof_url = ? WHERE payment_id = ? AND grantee_id = ?", filename, paymentId, userId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment not found for this student.");
        }
        return Map.of("message", "Proof uploaded successfully!");
    }

    private static Map<String, Object> first(List<Map<String, Object>> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
