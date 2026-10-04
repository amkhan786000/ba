package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/** Mirrors routes/sponsor.py (role_id 5). A sponsor is linked to students via
 *  one or more sponsor_references rows, not directly by user_id. */
@RestController
@RequestMapping("/api/sponsor")
@PreAuthorize("hasRole('5')")
public class SponsorController {

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorageService;

    public SponsorController(JdbcTemplate jdbc, FileStorageService fileStorageService) {
        this.jdbc = jdbc;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        String sponsorId = AuthUtil.currentUser().getUserId();
        Map<String, Object> sponsor = jdbc.queryForMap("SELECT * FROM users WHERE user_id = ?", sponsorId);

        List<Map<String, Object>> grantorGrantees = jdbc.queryForList("""
            SELECT gg.*, sr.reference_id AS linked_ref_id FROM grantor_grantees gg
            JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id WHERE sr.user_id = ?
            """, sponsorId);

        List<Map<String, Object>> grantees = new ArrayList<>();
        for (Map<String, Object> gg : grantorGrantees) {
            String granteeId = (String) gg.get("grantee_id");
            Map<String, Object> grantee = first(jdbc.queryForList("SELECT * FROM users WHERE user_id = ?", granteeId));
            Map<String, Object> bank = first(jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", granteeId));
            Map<String, Object> latestPayment = first(jdbc.queryForList(
                    "SELECT * FROM payments WHERE grantee_id = ? ORDER BY created_at DESC LIMIT 1", granteeId));
            Map<String, Object> courseInfo = first(jdbc.queryForList("""
                SELECT sic.assigned_at, c.number_of_semesters FROM student_institution_courses sic
                JOIN courses c ON sic.course_id = c.course_id WHERE sic.user_id = ?
                """, granteeId));
            String paymentStatus = (courseInfo != null && courseInfo.get("assigned_at") != null) ? "On Schedule" : "Pending";

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("user", grantee);
            entry.put("bankDetails", bank);
            entry.put("latestPayment", latestPayment);
            entry.put("paymentStatus", paymentStatus);
            entry.put("referenceId", gg.get("linked_ref_id"));
            grantees.add(entry);
        }

        return Map.of("sponsor", sponsor, "grantees", grantees);
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(@RequestParam(required = false) String granteeId) {
        String sponsorId = AuthUtil.currentUser().getUserId();
        List<Map<String, Object>> assignedStudents = jdbc.queryForList("""
            SELECT u.*, gg.grantor_id AS linked_ref_id FROM users u
            JOIN grantor_grantees gg ON u.user_id = gg.grantee_id
            JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id WHERE sr.user_id = ?
            """, sponsorId);

        List<Map<String, Object>> paymentDetails = new ArrayList<>();
        Map<String, Object> studentDataMap = new LinkedHashMap<>();
        for (Map<String, Object> student : assignedStudents) {
            String sId = (String) student.get("user_id");
            Map<String, Object> sched = first(jdbc.queryForList(
                    "SELECT amount FROM payment_schedules WHERE year = ? AND status = 1 LIMIT 1", student.get("year")));
            Map<String, Object> bank = first(jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", sId));
            List<Map<String, Object>> allPayments = jdbc.queryForList(
                    "SELECT * FROM payments WHERE grantee_id = ? AND status = 'Paid' ORDER BY payment_date DESC", sId);
            Map<String, Object> course = first(jdbc.queryForList("""
                SELECT sic.assigned_at, c.number_of_semesters, c.fees_per_semester FROM student_institution_courses sic
                JOIN courses c ON sic.course_id = c.course_id WHERE sic.user_id = ?
                """, sId));

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("grantee", student);
            detail.put("bankDetails", bank);
            detail.put("payments", allPayments);
            detail.put("courseInfo", course);
            detail.put("referenceId", student.get("linked_ref_id"));
            detail.put("annualScheduleAmount", sched == null ? 0 : sched.get("amount"));
            paymentDetails.add(detail);
            studentDataMap.put(sId, detail);
        }

        List<Map<String, Object>> pastPayments = jdbc.queryForList("""
            SELECT p.*, u.name AS grantee_name FROM payments p
            JOIN users u ON p.grantee_id = u.user_id
            JOIN sponsor_references sr ON p.grantor_id = sr.reference_id
            WHERE sr.user_id = ? AND p.status = 'Paid' ORDER BY p.payment_date DESC LIMIT 5
            """, sponsorId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentDetails", paymentDetails);
        result.put("pastPayments", pastPayments);
        result.put("studentDataMap", studentDataMap);
        result.put("selectedGranteeId", granteeId);
        return result;
    }

    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> recordPayment(@RequestParam String granteeId,
                                               @RequestParam BigDecimal amount,
                                               @RequestParam String paymentDate,
                                               @RequestParam MultipartFile receipt) {
        Map<String, Object> mapping = first(jdbc.queryForList(
                "SELECT grantor_id FROM grantor_grantees WHERE grantee_id = ? LIMIT 1", granteeId));
        if (mapping == null) {
            throw new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Error: Student is not linked to a valid sponsorship reference.");
        }
        String refId = (String) mapping.get("grantor_id");
        String filename = fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        String stored = fileStorageService.store(receipt, filename);
        jdbc.update("""
            INSERT INTO payments (grantor_id, grantee_id, amount, payment_date, receipt_url, status, created_at, updated_by)
            VALUES (?, ?, ?, ?, ?, 'Paid', NOW(), ?)
            """, refId, granteeId, amount, paymentDate, stored, AuthUtil.currentUser().getUserId());
        return Map.of("message", "Payment recorded successfully!");
    }

    @GetMapping("/student-progress")
    public List<Map<String, Object>> studentProgress() {
        return jdbc.queryForList("""
            SELECT sp.*, u.name AS grantee_name FROM student_progress sp
            JOIN users u ON sp.grantee_id = u.user_id
            JOIN grantor_grantees gg ON u.user_id = gg.grantee_id
            JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id
            WHERE sr.user_id = ?
            """, AuthUtil.currentUser().getUserId());
    }

    private static Map<String, Object> first(List<Map<String, Object>> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
