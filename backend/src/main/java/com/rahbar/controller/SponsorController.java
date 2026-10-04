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

/** Mirrors routes/sponsor.py (role_id 5). Students are linked to a sponsor directly:
 *  grantor_grantees.grantor_id = the sponsor's user_id. */
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
        Map<String, Object> sponsor = jdbc.queryForMap(
                "SELECT user_id, name, email, phone, region, status FROM users WHERE user_id = ?", sponsorId);

        List<Map<String, Object>> grantorGrantees = jdbc.queryForList("""
            SELECT gg.* FROM grantor_grantees gg WHERE gg.grantor_id = ?
            """, sponsorId);

        List<Map<String, Object>> grantees = new ArrayList<>();
        for (Map<String, Object> gg : grantorGrantees) {
            String granteeId = (String) gg.get("grantee_id");
            Map<String, Object> grantee = first(jdbc.queryForList(
                    "SELECT user_id, name, email, phone, region, status, year FROM users WHERE user_id = ?", granteeId));
            Map<String, Object> bank = first(jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", granteeId));
            Map<String, Object> latestPayment = first(jdbc.queryForList(
                    "SELECT * FROM payments WHERE grantee_id = ? ORDER BY created_at DESC LIMIT 1", granteeId));
            Map<String, Object> courseInfo = first(jdbc.queryForList("""
                SELECT sic.assigned_at, c.number_of_semesters FROM student_institution_courses sic
                JOIN courses c ON sic.course_id = c.course_id WHERE sic.user_id = ?
                """, granteeId));
            // Flask showed "On Schedule" for anyone with a course; this checks installments actually due vs paid.
            String paymentStatus = "Pending";
            if (courseInfo != null && courseInfo.get("assigned_at") != null) {
                java.time.LocalDate start = toLocalDate(courseInfo.get("assigned_at"));
                int semesters = courseInfo.get("number_of_semesters") == null ? 0 : ((Number) courseInfo.get("number_of_semesters")).intValue();
                int total = (int) Math.floor(semesters / 2.0 * 4);  // same as the JS schedule: (semesters / 2) * 4
                int dueSoFar = 0;
                for (int i = 1; i <= total; i++) {
                    if (!start.plusMonths(3L * i).isAfter(java.time.LocalDate.now())) dueSoFar++;
                }
                Long paid = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE grantee_id = ? AND status = 'Paid'", Long.class, granteeId);
                long paidCount = paid == null ? 0 : paid;
                if (total > 0 && paidCount >= total) paymentStatus = "Completed";
                else if (paidCount >= dueSoFar) paymentStatus = "On Schedule";
                else paymentStatus = "Overdue";
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("user", grantee);
            entry.put("bankDetails", bank);
            entry.put("latestPayment", latestPayment);
            entry.put("paymentStatus", paymentStatus);
            grantees.add(entry);
        }

        // Average marks per academic year of this sponsor's students (Flask's trend chart used sample numbers).
        List<Map<String, Object>> performance = jdbc.queryForList("""
            SELECT CONCAT('Year ', sp.year) AS label, ROUND(AVG(CAST(sp.marks AS DECIMAL(10,2))), 1) AS value
            FROM student_progress sp
            JOIN grantor_grantees gg ON sp.grantee_id = gg.grantee_id
            WHERE gg.grantor_id = ? AND sp.year IS NOT NULL
            GROUP BY sp.year ORDER BY sp.year
            """, sponsorId);

        return Map.of("sponsor", sponsor, "grantees", grantees, "performanceByYear", performance);
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(@RequestParam(required = false) String granteeId) {
        String sponsorId = AuthUtil.currentUser().getUserId();
        List<Map<String, Object>> assignedStudents = jdbc.queryForList("""
            SELECT u.user_id, u.name, u.email, u.phone, u.region, u.status, u.year FROM users u
            JOIN grantor_grantees gg ON u.user_id = gg.grantee_id WHERE gg.grantor_id = ?
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
            detail.put("annualScheduleAmount", sched == null ? 0 : sched.get("amount"));
            paymentDetails.add(detail);
            studentDataMap.put(sId, detail);
        }

        List<Map<String, Object>> pastPayments = jdbc.queryForList("""
            SELECT p.*, u.name AS grantee_name FROM payments p
            JOIN users u ON p.grantee_id = u.user_id
            WHERE p.grantor_id = ? AND p.status = 'Paid' ORDER BY p.payment_date DESC LIMIT 5
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
        // A sponsor can only record payments for students mapped to them.
        String sponsorId = AuthUtil.currentUser().getUserId();
        Long mapped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM grantor_grantees WHERE grantee_id = ? AND grantor_id = ?", Long.class, granteeId, sponsorId);
        if (mapped == null || mapped == 0) {
            throw new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Error: This student is not assigned to you.");
        }
        String filename = "sponsor_pay_" + granteeId + "_" + System.currentTimeMillis() + "_"
                + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        String stored = fileStorageService.store(receipt, filename);
        jdbc.update("""
            INSERT INTO payments (grantor_id, grantee_id, amount, payment_date, receipt_url, status, created_at, updated_by)
            VALUES (?, ?, ?, ?, ?, 'Paid', NOW(), ?)
            """, sponsorId, granteeId, amount, paymentDate, stored, sponsorId);
        return Map.of("message", "Payment recorded successfully!");
    }

    @GetMapping("/student-progress")
    public List<Map<String, Object>> studentProgress() {
        return jdbc.queryForList("""
            SELECT sp.*, u.name AS grantee_name FROM student_progress sp
            JOIN users u ON sp.grantee_id = u.user_id
            JOIN grantor_grantees gg ON u.user_id = gg.grantee_id
            WHERE gg.grantor_id = ?
            ORDER BY sp.created_at DESC
            """, AuthUtil.currentUser().getUserId());
    }

    private static java.time.LocalDate toLocalDate(Object v) {
        if (v instanceof java.time.LocalDateTime ldt) return ldt.toLocalDate();
        if (v instanceof java.time.LocalDate ld) return ld;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (v instanceof java.util.Date d) return new java.sql.Date(d.getTime()).toLocalDate();
        return java.time.LocalDate.parse(String.valueOf(v).substring(0, 10));
    }

    private static Map<String, Object> first(List<Map<String, Object>> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
