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

        // Per-student status. Flask computed one shared value from the last student only (and crashed when a
        // student had no payments); here each student is "paid" if their latest payment is within the last year.
        List<Map<String, Object>> granteesWithStatus = new ArrayList<>();
        for (Map<String, Object> g : grantees) {
            Map<String, Object> row = new LinkedHashMap<>(g);
            row.remove("password_hash");
            Long recent = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE grantee_id = ? AND payment_date >= DATE_SUB(NOW(), INTERVAL 365 DAY)",
                    Long.class, g.get("user_id"));
            row.put("paymentStatus", recent != null && recent > 0 ? "paid" : "unpaid");
            granteesWithStatus.add(row);
        }

        // Chart data (the Flask page drew these from hard-coded sample numbers)
        List<Map<String, Object>> byStatus = jdbc.queryForList("""
            SELECT COALESCE(s.status, 'no status') AS label, COUNT(*) AS value
            FROM grantee_details gd
            JOIN users u ON gd.user_id = u.user_id
            LEFT JOIN (SELECT grantee_detail_id, MAX(created_at) AS latest FROM application_status GROUP BY grantee_detail_id) ls
              ON gd.grantee_detail_id = ls.grantee_detail_id
            LEFT JOIN application_status s ON s.grantee_detail_id = ls.grantee_detail_id AND s.created_at = ls.latest
            WHERE u.region = ?
            GROUP BY label ORDER BY value DESC
            """, region);
        List<Map<String, Object>> byRegion = jdbc.queryForList("""
            SELECT COALESCE(NULLIF(TRIM(region), ''), 'Not set') AS label, COUNT(*) AS value
            FROM users WHERE role_id = 5 GROUP BY label ORDER BY value DESC
            """);

        convenor = new LinkedHashMap<>(convenor);
        convenor.remove("password_hash");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("convenor", convenor);
        result.put("applications", applications);
        result.put("grantees", granteesWithStatus);
        result.put("applicationsByStatus", byStatus);
        result.put("sponsorsByRegion", byRegion);
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
        jdbc.update("UPDATE grantee_details SET status = ?, comments = ?, updated_by = ?, updated_at = NOW() WHERE application_id = ?",
                body.get("status"), body.get("comments"), me(), applicationId);
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
        jdbc.update("UPDATE users SET status = ?, updated_by = ?, updated_at = NOW() WHERE user_id = ?", status, me(), sponsorId);
        if ("Inactive".equalsIgnoreCase(status)) {
            jdbc.update("UPDATE grantor_grantees SET grantor_id = 12, updated_by = ?, updated_at = NOW() WHERE grantor_id = ?", me(), sponsorId);
        }
        return Map.of("message", "Sponsor status updated to " + status + "!");
    }

    @PostMapping("/map-students/{sponsorId}")
    public Map<String, String> mapStudents(@PathVariable String sponsorId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> studentIds = (List<String>) body.get("studentIds");
        for (String studentId : studentIds) {
            jdbc.update("UPDATE grantor_grantees SET grantor_id = ?, updated_by = ?, updated_at = NOW() WHERE grantee_id = ?", sponsorId, me(), studentId);
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
        else sql.append(" ORDER BY sp.created_at DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @PostMapping("/profile")
    public Map<String, String> updateProfile(@RequestBody Map<String, String> body) {
        jdbc.update("UPDATE users SET region = ?, updated_by = ?, updated_at = NOW() WHERE user_id = ?", body.get("region"), me(), AuthUtil.currentUser().getUserId());
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
            List<Map<String, Object>> students = jdbc.queryForList(
                    "SELECT user_id, name, email, phone, region, status, year FROM users WHERE user_id IN (" + ph + ")", studentIdsStr.toArray());
            studentsForDropdown.addAll(students);
            for (Map<String, Object> s : students) {
                String sid = (String) s.get("user_id");
                Map<String, Object> bank = jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", sid)
                        .stream().findFirst().orElse(null);
                Map<String, Object> courseInfo = jdbc.queryForList("""
                    SELECT sic.assigned_at, c.number_of_semesters, c.fees_per_semester
                    FROM student_institution_courses sic JOIN courses c ON sic.course_id = c.course_id
                    WHERE sic.user_id = ?
                    """, sid).stream().findFirst().orElse(null);
                List<Map<String, Object>> paidRecords = jdbc.queryForList(
                        "SELECT * FROM payments WHERE grantee_id = ? AND (status='Paid' OR status='pending') ORDER BY payment_date ASC", sid);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("grantee", s);
                entry.put("bankDetails", bank == null ? Map.of() : bank);
                entry.put("courseInfo", courseInfo);
                entry.put("paidRecords", paidRecords);
                studentDataMap.put(sid, entry);
            }
        }

        List<Map<String, Object>> pastPayments = jdbc.queryForList(
                "SELECT p.*, u.name AS grantee_name FROM payments p JOIN users u ON p.grantee_id = u.user_id WHERE p.grantor_id = ?", convenorId);

        convenor = new LinkedHashMap<>(convenor);
        convenor.remove("password_hash");

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
        // Prefixed so two receipts with the same original name don't overwrite each other.
        String filename = "convenor_pay_" + granteeId + "_" + System.currentTimeMillis() + "_"
                + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        String path = fileStorageService.store(receipt, filename);
        jdbc.update("""
            INSERT INTO payments (grantor_id, grantee_id, amount, payment_date, receipt_url, status, created_by, updated_by)
            VALUES (?, ?, ?, NOW(), ?, 'pending', ?, ?)
            """, convenorId, granteeId, amount, path, convenorId, convenorId);
        return Map.of("message", "Payment recorded and is now pending approval.");
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadFile(@RequestParam MultipartFile file) {
        String filename = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        fileStorageService.store(file, filename);
        return Map.of("message", "File uploaded successfully!");
    }

    /** user_id of the logged-in user, written to created_by / updated_by on raw-SQL writes. */
    private static String me() {
        return com.rahbar.config.AuditConfig.currentUserId();
    }

}
