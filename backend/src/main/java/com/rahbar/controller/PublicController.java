package com.rahbar.controller;

import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the public-facing bits of routes/admin.py: /apply, /application_status.
 *
 * NOTE: in the original Flask code, public_application() (the public
 * scholarship application form) was annotated @login_required, which
 * contradicts its own "public application" purpose and its template name
 * (public/apply.html). That looks like a bug, so this migration makes the
 * endpoint genuinely public, matching the apparent intent. Flag this to the
 * product owner in case the login requirement was actually deliberate.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final JdbcTemplate jdbc;

    public PublicController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/application-form-options")
    public Map<String, Object> formOptions() {
        List<Map<String, Object>> period = jdbc.queryForList(
                "SELECT * FROM application_period WHERE is_active = 1 AND start_date <= CURDATE() AND end_date >= CURDATE()");
        List<Map<String, Object>> rccCenters = jdbc.queryForList("SELECT * FROM rcc_centers");
        List<Map<String, Object>> courses = jdbc.queryForList("SELECT * FROM courses");
        return Map.of("periodOpen", !period.isEmpty(), "rccCenters", rccCenters, "courses", courses);
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody Map<String, Object> form) {
        boolean periodOpen = !jdbc.queryForList(
                "SELECT * FROM application_period WHERE is_active = 1 AND start_date <= CURDATE() AND end_date >= CURDATE()").isEmpty();
        if (!periodOpen) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Applications are currently closed.");
        }

        String fatherMobile = str(form, "fatherMobile");
        String motherMobile = str(form, "motherMobile");
        String studentMobile = str(form, "studentMobile");
        Set<String> mobiles = Set.of(fatherMobile, motherMobile, studentMobile);
        if (mobiles.size() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "All three mobile numbers must be different");
        }
        for (String m : mobiles) {
            if (m == null || !m.matches("\\d{10}")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid mobile numbers. Must be 10 digits");
            }
        }

        jdbc.update("""
            INSERT INTO grantee_details
            (user_id, name, father_name, mother_name, father_profession, mother_profession,
             address, average_annual_salary, rahbar_alumnus, rcc_name, course_applied,
             father_mobile, mother_mobile, student_mobile, created_at, updated_at)
            VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
                str(form, "name"), str(form, "fatherName"), str(form, "motherName"),
                str(form, "fatherProfession"), str(form, "motherProfession"), str(form, "address"),
                form.get("averageAnnualSalary"), Boolean.TRUE.equals(form.get("rahbarAlumnus")) ? 1 : 0,
                str(form, "rccName"), str(form, "courseApplied"), fatherMobile, motherMobile, studentMobile);

        Long granteeDetailId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
            INSERT INTO application_status (grantee_detail_id, status, comments, updated_by)
            VALUES (?, 'submitted', 'Application submitted', NULL)
            """, granteeDetailId);

        return Map.of("message", "Application submitted successfully!", "applicationId", granteeDetailId);
    }

    @GetMapping("/application-status/{applicationId}")
    public Map<String, Object> applicationStatus(@PathVariable Long applicationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT gd.*, s.status, s.comments, s.created_at AS status_date
            FROM grantee_details gd
            LEFT JOIN application_status s ON gd.grantee_detail_id = s.grantee_detail_id
            WHERE gd.grantee_detail_id = ? ORDER BY s.created_at DESC
            """, applicationId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invalid application ID");
        }
        return rows.get(0);
    }

    @PostMapping("/check-application-status")
    public Map<String, Object> checkByMobile(@RequestBody Map<String, String> body) {
        String mobile = body.get("mobile");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT grantee_detail_id FROM grantee_details
            WHERE student_mobile = ? OR father_mobile = ? OR mother_mobile = ?
            """, mobile, mobile, mobile);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No application found with this mobile number");
        }
        return Map.of("applicationId", rows.get(0).get("grantee_detail_id"));
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
