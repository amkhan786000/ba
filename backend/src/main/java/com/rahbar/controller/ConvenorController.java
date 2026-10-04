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

import java.math.BigDecimal;
import java.util.*;

/** Mirrors routes/convenor.py (role_id 4). */
@RestController
@RequestMapping("/api/convenor")
@PreAuthorize("hasRole('4')")
public class ConvenorController {

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorageService;

    public ConvenorController(JdbcTemplate jdbc, FileStorageService fileStorageService) {
        this.jdbc = jdbc;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        String convenorId = AuthUtil.currentUser().getUserId();
        Map<String, Object> convenor = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", convenorId);
        String region = (String) convenor.get("region");
        if (region == null || region.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your region is not set. Please update your profile.");
        }

        List<Map<String, Object>> applications = jdbc.queryForList("""
            SELECT a.*, u.name AS applicant_name FROM grantee_details a JOIN users u ON a.user_id = u.user_id
            WHERE u.region = ?
            """, region);
        List<Map<String, Object>> grantorGrantee = jdbc.queryForList(
                "SELECT * FROM grantor_grantees WHERE grantor_id = ?", convenorId);
        List<Map<String, Object>> grantees = new ArrayList<>();
        for (Map<String, Object> gg : grantorGrantee) {
            jdbc.queryForList("SELECT * FROM users WHERE user_id = ?", gg.get("grantee_id"))
                    .stream().findFirst().ifPresent(grantees::add);
        }
        List<Map<String, Object>> sponsors = jdbc.queryForList("""
            SELECT u.* FROM users u JOIN grantor_grantees gg ON u.user_id = gg.grantor_id
            JOIN grantee_details gd ON gg.grantee_id = gd.user_id WHERE u.region = ?
            """, region);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("convenor", convenor);
        result.put("applications", applications);
        result.put("grantees", grantees);
        result.put("sponsors", sponsors);
        result.put("grantorGrantee", grantorGrantee);
        return result;
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> viewApplications(@RequestParam(defaultValue = "application_id") String sortBy,
                                                        @RequestParam(defaultValue = "asc") String order) {
        List<String> validCols = List.of("application_id", "applicant_name", "status", "date_submitted");
        String col = validCols.contains(sortBy) ? sortBy : "application_id";
        String dir = "desc".equalsIgnoreCase(order) ? "desc" : "asc";
        String region = (String) jdbc.queryForMap("SELECT region FROM users WHERE user_id = ?", AuthUtil.currentUser().getUserId()).get("region");
        if (region == null || region.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your region is not set. Please update your profile.");
        }
        return jdbc.queryForList(
                "SELECT a.*, u.name AS applicant_name FROM grantee_details a JOIN users u ON a.user_id = u.user_id " +
                "WHERE u.region = ? ORDER BY " + col + " " + dir, region);
    }

    @PostMapping("/applications/{applicationId}/status")
    public Map<String, String> updateApplicationStatus(@PathVariable Long applicationId, @RequestBody Map<String, String> body) {
        jdbc.update("UPDATE grantee_details SET status = ?, comments = ? WHERE application_id = ?",
                body.get("status"), body.get("comments"), applicationId);
        return Map.of("message", "Application status updated successfully!");
    }

    @GetMapping("/manage-sponsors")
    public Map<String, Object> manageSponsors(@RequestParam(defaultValue = "user_id") String sortBy,
                                                @RequestParam(defaultValue = "asc") String order) {
        List<String> validCols = List.of("user_id", "name", "email", "status");
        String col = validCols.contains(sortBy) ? sortBy : "user_id";
        String dir = "desc".equalsIgnoreCase(order) ? "desc" : "asc";
        String region = (String) jdbc.queryForMap("SELECT region FROM users WHERE user_id = ?", AuthUtil.currentUser().getUserId()).get("region");
        if (region == null || region.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your region is not set. Please update your profile.");
        }
        List<Map<String, Object>> sponsors = jdbc.queryForList(
                "SELECT u.* FROM users u WHERE u.role_id = 5 AND u.region = ? ORDER BY " + col + " " + dir, region);
        List<Map<String, Object>> nonAssigned = jdbc.queryForList(
                "SELECT u.* FROM users u JOIN grantor_grantees gg ON u.user_id = gg.grantee_id WHERE gg.grantor_id = 12");
        return Map.of("sponsors", sponsors, "nonAssignedGrantees", nonAssigned);
    }

    @PostMapping("/sponsors/{sponsorId}/status/{status}")
    public Map<String, String> updateSponsorStatus(@PathVariable String sponsorId, @PathVariable String status) {
        jdbc.update("UPDATE users SET status = ? WHERE user_id = ?", status, sponsorId);
        if ("Inactive".equalsIgnoreCase(status)) {
            jdbc.update("UPDATE grantor_grantees SET grantor_id = 12 WHERE grantor_id = ?", sponsorId);
        }
        return Map.of("message", "Sponsor status updated to " + status + "!");
    }

    @PostMapping("/map-students/{sponsorId}")
    public Map<String, String> mapStudents(@PathVariable String sponsorId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> studentIds = (List<String>) body.get("studentIds");
        for (String studentId : studentIds) {
            jdbc.update("UPDATE grantor_grantees SET grantor_id = ? WHERE grantee_id = ?", sponsorId, studentId);
        }
        return Map.of("message", "Students mapped successfully!");
    }

    @GetMapping("/student-progress")
    public List<Map<String, Object>> viewStudentProgress(@RequestParam(required = false) String granteeName,
                                                           @RequestParam(required = false) Double minMarks,
                                                           @RequestParam(required = false) Double maxMarks,
                                                           @RequestParam(required = false) String startDate,
                                                           @RequestParam(required = false) String endDate,
                                                           @RequestParam(required = false) String sortBy) {
        String region = (String) jdbc.queryForMap("SELECT region FROM users WHERE user_id = ?", AuthUtil.currentUser().getUserId()).get("region");
        StringBuilder sql = new StringBuilder("""
            SELECT sp.*, u.name AS grantee_name FROM student_progress sp
            JOIN users u ON sp.grantee_id = u.user_id WHERE u.region = ?
            """);
        List<Object> params = new ArrayList<>(List.of(region));
        if (granteeName != null) { sql.append(" AND u.name LIKE ?"); params.add("%" + granteeName + "%"); }
        if (minMarks != null) { sql.append(" AND sp.marks >= ?"); params.add(minMarks); }
        if (maxMarks != null) { sql.append(" AND sp.marks <= ?"); params.add(maxMarks); }
        if (startDate != null) { sql.append(" AND sp.created_at >= ?"); params.add(startDate); }
        if (endDate != null) { sql.append(" AND sp.created_at <= ?"); params.add(endDate); }
        if ("marks".equals(sortBy)) sql.append(" ORDER BY sp.marks DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @PostMapping("/profile")
    public Map<String, String> updateProfile(@RequestBody Map<String, String> body) {
        jdbc.update("UPDATE users SET region = ? WHERE user_id = ?", body.get("region"), AuthUtil.currentUser().getUserId());
        return Map.of("message", "Profile updated successfully!");
    }

    @GetMapping("/payments")
    public Map<String, Object> payments() {
        String convenorId = AuthUtil.currentUser().getUserId();
        Map<String, Object> convenor = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", convenorId);

        List<String> studentIdsStr = jdbc.queryForList(
                "SELECT grantee_id FROM grantor_grantees WHERE grantor_id = ?", String.class, convenorId);

        Map<String, Object> studentDataMap = new LinkedHashMap<>();
        List<Map<String, Object>> studentsForDropdown = new ArrayList<>();
        if (!studentIdsStr.isEmpty()) {
            String ph = String.join(",", Collections.nCopies(studentIdsStr.size(), "?"));
            List<Map<String, Object>> students = jdbc.queryForList("SELECT * FROM users WHERE user_id IN (" + ph + ")", studentIdsStr.toArray());
            studentsForDropdown.addAll(students);
            for (Map<String, Object> s : students) {
                String sid = (String) s.get("user_id");
                Map<String, Object> bank = jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", sid)
                        .stream().findFirst().orElse(null);
                List<Map<String, Object>> paidRecords = jdbc.queryForList(
                        "SELECT * FROM payments WHERE grantee_id = ? AND (status='Paid' OR status='pending') ORDER BY payment_date ASC", sid);
                studentDataMap.put(sid, Map.of("grantee", s, "bankDetails", bank == null ? Map.of() : bank, "paidRecords", paidRecords));
            }
        }

        List<Map<String, Object>> pastPayments = jdbc.queryForList(
                "SELECT p.*, u.name AS grantee_name FROM payments p JOIN users u ON p.grantee_id = u.user_id WHERE p.grantor_id = ?", convenorId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("convenor", convenor);
        result.put("studentsForDropdown", studentsForDropdown);
        result.put("studentDataMap", studentDataMap);
        result.put("pastPayments", pastPayments);
        return result;
    }

    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> recordPayment(@RequestParam String granteeId,
                                               @RequestParam BigDecimal amount,
                                               @RequestParam MultipartFile receipt) {
        String convenorId = AuthUtil.currentUser().getUserId();
        String filename = fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        String path = fileStorageService.store(receipt, filename);
        jdbc.update("""
            INSERT INTO payments (grantor_id, grantee_id, amount, payment_date, receipt_url, status)
            VALUES (?, ?, ?, NOW(), ?, 'pending')
            """, convenorId, granteeId, amount, path);
        return Map.of("message", "Payment recorded and is now pending approval.");
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadFile(@RequestParam MultipartFile file) {
        String filename = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        fileStorageService.store(file, filename);
        return Map.of("message", "File uploaded successfully!");
    }
}
