package com.rahbar.controller;

import com.rahbar.exception.ApiException;
import com.rahbar.security.AuthUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/** Mirrors routes/coordinator.py (role_id 3). */
@RestController
@RequestMapping("/api/coordinator")
@PreAuthorize("hasRole('3')")
public class CoordinatorController {

    private final JdbcTemplate jdbc;

    public CoordinatorController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) Integer year) {
        List<Integer> years = jdbc.queryForList("""
            (SELECT DISTINCT YEAR(created_at) AS year FROM grantee_details WHERE created_at IS NOT NULL)
            UNION
            (SELECT DISTINCT YEAR(created_at) AS year FROM users WHERE (role_id = 5 OR role_id = 4) AND created_at IS NOT NULL)
            UNION
            (SELECT DISTINCT YEAR(created_at) AS year FROM users WHERE role_id = 6 AND created_at IS NOT NULL)
            ORDER BY year DESC
            """, Integer.class);
        int selectedYear = year != null ? year : (years.isEmpty() ? java.time.Year.now().getValue() : years.get(0));

        long applications = jdbc.queryForObject("SELECT COUNT(*) FROM grantee_details WHERE YEAR(created_at) = ?", Long.class, selectedYear);
        long sponsors = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role_id = 5 AND YEAR(created_at) = ?", Long.class, selectedYear);
        long grantees = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role_id = 6 AND YEAR(created_at) = ?", Long.class, selectedYear);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("availableYears", years);
        result.put("selectedYear", selectedYear);
        result.put("applicationsCount", applications);
        result.put("sponsorsCount", sponsors);
        result.put("granteesCount", grantees);
        return result;
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> viewApplications() {
        return jdbc.queryForList("""
            SELECT gd.*, u.name AS applicant_name FROM grantee_details gd JOIN users u ON gd.user_id = u.user_id
            """);
    }

    @PostMapping("/assign-sponsor")
    public Map<String, String> assignSponsor(@RequestBody Map<String, String> body) {
        String granteeId = body.get("granteeId");
        String grantorId = body.get("grantorId");
        boolean exists = !jdbc.queryForList(
                "SELECT * FROM grantor_grantees WHERE grantee_id = ? AND grantor_id != 12", granteeId).isEmpty();
        if (exists) {
            jdbc.update("UPDATE grantor_grantees SET grantor_id=?, status='Assigned', updated_at=NOW() WHERE grantee_id=?", grantorId, granteeId);
        } else {
            jdbc.update("INSERT INTO grantor_grantees (grantor_id, grantee_id, status, created_at, updated_at) VALUES (?,?,'Assigned',NOW(),NOW())", grantorId, granteeId);
        }
        return Map.of("message", "Sponsor assigned successfully!");
    }

    @PostMapping("/users/{userId}/status/{status}")
    public Map<String, String> updateUserStatus(@PathVariable String userId, @PathVariable String status) {
        jdbc.update("UPDATE users SET status = ? WHERE user_id = ?", status, userId);
        if ("Inactive".equalsIgnoreCase(status)) {
            jdbc.update("UPDATE grantor_grantees SET grantor_id = 12, status = 'Unassigned' WHERE grantor_id = ?", userId);
        }
        return Map.of("message", "User status updated to " + status + ", and grantees reassigned to default grantor (ID: 12).");
    }

    @GetMapping("/map-students/{sponsorId}")
    public Map<String, Object> mapStudentsScreen(@PathVariable String sponsorId) {
        List<Map<String, Object>> students = jdbc.queryForList(
                "SELECT u.* FROM users u JOIN grantor_grantees gg ON u.user_id = gg.grantee_id WHERE gg.grantor_id = 12");
        List<Map<String, Object>> mapped = jdbc.queryForList(
                "SELECT u.* FROM users u JOIN grantor_grantees gg ON u.user_id = gg.grantee_id WHERE gg.grantor_id = ?", sponsorId);
        return Map.of("students", students, "mappedStudents", mapped,
                "mappedStudentIds", mapped.stream().map(m -> m.get("user_id")).toList());
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

    @GetMapping("/manage-sponsors")
    public Map<String, Object> manageSponsors() {
        List<Map<String, Object>> sponsorsConvenors = jdbc.queryForList("""
            SELECT u.*, r.role_name, r.description FROM users u JOIN roles r ON u.role_id = r.role_id
            WHERE u.role_id IN (4, 5)
            """);
        List<Map<String, Object>> grantees = jdbc.queryForList("SELECT * FROM users WHERE role_id = 6");
        return Map.of("sponsorsConvenors", sponsorsConvenors, "grantees", grantees);
    }

    @PostMapping("/appoint-convenor/{sponsorId}")
    public Map<String, String> appointConvenor(@PathVariable String sponsorId, @RequestBody Map<String, String> body) {
        jdbc.update("UPDATE users SET role_id = 4, region = ? WHERE user_id = ?", body.get("region"), sponsorId);
        return Map.of("message", "Sponsor appointed as Convenor successfully!");
    }

    @PostMapping("/users/{userId}/region")
    public Map<String, String> changeRegion(@PathVariable String userId, @RequestBody Map<String, String> body) {
        jdbc.update("UPDATE users SET region = ? WHERE user_id = ?", body.get("region"), userId);
        return Map.of("message", "Region updated successfully!");
    }

    @PostMapping("/assign-students-bulk")
    public Map<String, String> assignStudentsBulk(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> studentIds = (List<String>) body.get("studentIds");
        String sponsorId = String.valueOf(body.get("sponsorId"));
        for (String studentId : studentIds) {
            boolean exists = !jdbc.queryForList(
                    "SELECT * FROM grantor_grantees WHERE grantee_id = ? AND grantor_id != 12", studentId).isEmpty();
            if (exists) {
                jdbc.update("UPDATE grantor_grantees SET grantor_id=?, status='Assigned', updated_at=NOW() WHERE grantee_id=?", sponsorId, studentId);
            } else {
                jdbc.update("INSERT INTO grantor_grantees (grantor_id, grantee_id, status, created_at, updated_at) VALUES (?,?,'Assigned',NOW(),NOW())", sponsorId, studentId);
            }
        }
        return Map.of("message", "Students assigned to sponsor successfully!");
    }

    @GetMapping("/sponsors-convenors")
    public List<Map<String, Object>> viewSponsorsConvenors() {
        return jdbc.queryForList("""
            SELECT u.*, r.role_name, r.description FROM users u JOIN roles r ON u.role_id = r.role_id
            WHERE u.role_id IN (4, 5)
            """);
    }

    @GetMapping("/monitor-payments")
    public Map<String, Object> monitorPayments(@RequestParam(defaultValue = "0") int start,
                                                 @RequestParam(defaultValue = "10") int length,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(defaultValue = "grantee_name") String orderBy,
                                                 @RequestParam(defaultValue = "asc") String orderDir) {
        List<String> allowedColumns = List.of("grantee_name", "grantor_name", "amount", "status", "receipt_url");
        String col = allowedColumns.contains(orderBy) ? orderBy : "grantee_name";
        String dir = "desc".equalsIgnoreCase(orderDir) ? "desc" : "asc";

        String base = """
            FROM payments p
            JOIN users u1 ON p.grantee_id = u1.user_id
            JOIN users u2 ON p.grantor_id = u2.user_id
            """;
        List<Object> params = new ArrayList<>();
        String where = "";
        if (search != null && !search.isBlank()) {
            where = " WHERE u1.name LIKE ? OR u1.user_id LIKE ? OR u1.phone LIKE ? OR u2.name LIKE ? OR u2.user_id LIKE ? OR u2.phone LIKE ? OR p.status LIKE ?";
            String w = "%" + search + "%";
            for (int i = 0; i < 7; i++) params.add(w);
        }

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
        long filtered = jdbc.queryForObject("SELECT COUNT(*) " + base + where, Long.class, params.toArray());

        List<Object> finalParams = new ArrayList<>(params);
        finalParams.add(start);
        finalParams.add(length);
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT u1.name AS grantee_name, u1.user_id AS grantee_id, u1.phone AS grantee_phone,
                   u2.name AS grantor_name, u2.user_id AS grantor_id, u2.phone AS grantor_phone,
                   p.amount, p.status, p.receipt_url, p.payment_id
            """ + base + where + " ORDER BY " + col + " " + dir + " LIMIT ?, ?", finalParams.toArray());

        return Map.of("recordsTotal", total, "recordsFiltered", filtered, "data", data);
    }

    @GetMapping("/reports")
    public ResponseEntity<ByteArrayResource> generateReports(@RequestParam String reportType,
                                                               @RequestParam(defaultValue = "csv") String format) throws IOException {
        List<Map<String, Object>> data;
        switch (reportType) {
            case "applications" -> data = jdbc.queryForList(
                    "SELECT * FROM grantee_details g JOIN application_status a ON g.grantee_detail_id = a.grantee_detail_id");
            case "payments" -> data = jdbc.queryForList("SELECT * FROM payments");
            case "sponsors_convenors" -> data = jdbc.queryForList("SELECT * FROM users WHERE role_id IN (4, 5)");
            case "grantees" -> data = jdbc.queryForList("SELECT * FROM users WHERE role_id = 6");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type");
        }
        if (data.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "No data available for " + reportType + " report.");

        byte[] bytes;
        MediaType mediaType;
        String filename = reportType + "_report";
        if ("excel".equals(format)) {
            bytes = ReportUtil.toExcel(data);
            mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            filename += ".xlsx";
        } else {
            bytes = ReportUtil.toCsv(data);
            mediaType = MediaType.parseMediaType("text/csv");
            filename += ".csv";
        }
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(bytes));
    }
}
