package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.StudentService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Mirrors routes/student.py (role_id 6 = beneficiary / grantee). */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('6')")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    private static String me() {
        return AuthUtil.currentUser().getUserId();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return studentService.dashboard(me());
    }

    @GetMapping("/payments")
    public Map<String, Object> payments() {
        return studentService.payments(me());
    }

    @GetMapping("/bank-details")
    public Map<String, Object> getBankDetails() {
        Map<String, Object> bank = studentService.bankDetails(me());
        return bank == null ? Map.of() : bank;
    }

    @PostMapping("/bank-details")
    public Map<String, Object> saveBankDetails(@RequestBody Map<String, String> body) {
        studentService.saveBankDetails(me(), body);
        return Map.of("success", true, "message", "Bank details updated successfully!");
    }

    @PostMapping(value = "/progress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> submitProgress(@RequestParam String marks,
                                              @RequestParam String year,
                                              @RequestParam String session,
                                              @RequestParam MultipartFile file) {
        studentService.submitProgress(me(), marks, year, session, file);
        return Map.of("message", "Progress submitted successfully!");
    }

    @GetMapping("/progress")
    public List<Map<String, Object>> progressHistory() {
        return studentService.progressHistory(me());
    }

    @PostMapping(value = "/payments/{paymentId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadPaymentProof(@PathVariable Long paymentId, @RequestParam MultipartFile proofFile) {
        studentService.uploadPaymentProof(me(), paymentId, proofFile);
        return Map.of("message", "Proof uploaded successfully!");
    }
}
