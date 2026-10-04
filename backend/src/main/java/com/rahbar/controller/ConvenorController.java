package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.ConvenorService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Mirrors routes/convenor.py (role_id 4). */
@RestController
@RequestMapping("/api/convenor")
@PreAuthorize("hasRole('4')")
public class ConvenorController {

    private final ConvenorService convenorService;

    public ConvenorController(ConvenorService convenorService) {
        this.convenorService = convenorService;
    }

    private static String me() {
        return AuthUtil.currentUser().getUserId();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return convenorService.dashboard(me());
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> viewApplications(@RequestParam(defaultValue = "application_id") String sortBy,
                                                      @RequestParam(defaultValue = "asc") String order) {
        return convenorService.applications(me(), sortBy, order);
    }

    @PostMapping("/applications/{applicationId}/status")
    public Map<String, String> updateApplicationStatus(@PathVariable Long applicationId, @RequestBody Map<String, String> body) {
        convenorService.updateApplicationStatus(applicationId, body.get("status"), body.get("comments"));
        return Map.of("message", "Application status updated successfully!");
    }

    @GetMapping("/manage-sponsors")
    public Map<String, Object> manageSponsors(@RequestParam(defaultValue = "user_id") String sortBy,
                                              @RequestParam(defaultValue = "asc") String order) {
        return convenorService.manageSponsors(me(), sortBy, order);
    }

    @PostMapping("/sponsors/{sponsorId}/status/{status}")
    public Map<String, String> updateSponsorStatus(@PathVariable String sponsorId, @PathVariable String status) {
        convenorService.updateSponsorStatus(sponsorId, status);
        return Map.of("message", "Sponsor status updated to " + status + "!");
    }

    @PostMapping("/map-students/{sponsorId}")
    @SuppressWarnings("unchecked")
    public Map<String, String> mapStudents(@PathVariable String sponsorId, @RequestBody Map<String, Object> body) {
        convenorService.mapStudents(sponsorId, (List<String>) body.get("studentIds"));
        return Map.of("message", "Students mapped successfully!");
    }

    @GetMapping("/student-progress")
    public List<Map<String, Object>> viewStudentProgress(@RequestParam(required = false) String granteeName,
                                                         @RequestParam(required = false) Double minMarks,
                                                         @RequestParam(required = false) Double maxMarks,
                                                         @RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate,
                                                         @RequestParam(required = false) String sortBy) {
        return convenorService.studentProgress(me(), granteeName, minMarks, maxMarks, startDate, endDate, sortBy);
    }

    @PostMapping("/profile")
    public Map<String, String> updateProfile(@RequestBody Map<String, String> body) {
        convenorService.updateRegion(me(), body.get("region"));
        return Map.of("message", "Profile updated successfully!");
    }

    @GetMapping("/payments")
    public Map<String, Object> payments() {
        return convenorService.payments(me());
    }

    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> recordPayment(@RequestParam String granteeId,
                                             @RequestParam BigDecimal amount,
                                             @RequestParam MultipartFile receipt) {
        convenorService.recordPayment(me(), granteeId, amount, receipt);
        return Map.of("message", "Payment recorded and is now pending approval.");
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadFile(@RequestParam MultipartFile file) {
        convenorService.uploadFile(file);
        return Map.of("message", "File uploaded successfully!");
    }
}
