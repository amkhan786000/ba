package com.rahbar.controller;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.AuthUtil;
import com.rahbar.service.FileStorageService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Mirrors routes/admin.py (role_id 1 = Super Admin, 2 = Application Administrator).
 * Read-heavy, join-heavy listing/report endpoints use JdbcTemplate to stay close
 * to the original hand-written SQL (and its COLLATE workarounds, kept for safety
 * in case this runs against the existing production database rather than the
 * V1 baseline shipped with this migration).
 *
 * Not ported from the original admin.py:
 *  - the PDF branch of generate_reports (it depended on an optional/uninstalled
 *    weasyprint library in the original code too; CSV/Excel are supported).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('1','2')")
public class AdminController {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationPeriodRepository applicationPeriodRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final RccCenterRepository rccCenterRepository;
    private final CourseRepository courseRepository;
    private final InstitutionRepository institutionRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public AdminController(JdbcTemplate jdbc, UserRepository userRepository, RoleRepository roleRepository,
                            ApplicationPeriodRepository applicationPeriodRepository,
                            PaymentScheduleRepository paymentScheduleRepository,
                            RccCenterRepository rccCenterRepository, CourseRepository courseRepository,
                            InstitutionRepository institutionRepository,
                            ApplicationStatusRepository applicationStatusRepository,
                            PasswordEncoder passwordEncoder, FileStorageService fileStorageService) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.courseRepository = courseRepository;
        this.institutionRepository = institutionRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    // ---------------------------------------------------------------- dashboard

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) Integer year) {
        // Newest year first. Nulls are filtered out here: a comparator-sorted TreeSet throws on null.
        Set<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        for (String sql : List.of(
                "SELECT DISTINCT YEAR(created_at) FROM users WHERE created_at IS NOT NULL",
                "SELECT DISTINCT YEAR(created_at) FROM grantee_details WHERE created_at IS NOT NULL",
                "SELECT DISTINCT YEAR(payment_date) FROM payments WHERE payment_date IS NOT NULL")) {
            jdbc.queryForList(sql, Integer.class).stream().filter(Objects::nonNull).forEach(years::add);
        }

        Object[] p = year != null ? new Object[]{year} : new Object[]{};
        String yf = year != null ? " WHERE YEAR(created_at) = ?" : "";
        String yfPay = year != null ? " WHERE YEAR(payment_date) = ?" : "";

        long usersCount = jdbc.queryForObject("SELECT COUNT(*) FROM users" + yf, Long.class, p);
        long applicationsCount = jdbc.queryForObject("SELECT COUNT(*) FROM grantee_details" + yf, Long.class, p);
        long paymentsCount = jdbc.queryForObject("SELECT COUNT(*) FROM payments" + yfPay, Long.class, p);

        String sponsorsQuery = "SELECT COUNT(*) FROM users WHERE role_id IN (4,5) AND status LIKE 'Active'" +
                (year != null ? " AND YEAR(created_at) = ?" : "");
        long sponsorsConvenorsCount = jdbc.queryForObject(sponsorsQuery, Long.class, p);

        Map<String, Object> applicationPeriod;
        try {
            applicationPeriod = jdbc.queryForMap("SELECT * FROM application_period WHERE id = 1 AND is_active = 1");
        } catch (Exception e) {
            applicationPeriod = null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("usersCount", usersCount);
        result.put("applicationsCount", applicationsCount);
        result.put("paymentsCount", paymentsCount);
        result.put("sponsorsConvenorsCount", sponsorsConvenorsCount);
        result.put("availableYears", years);
        result.put("selectedYear", year);
        result.put("applicationPeriod", applicationPeriod);
        return result;
    }

    // ---------------------------------------------------------- application period

    @GetMapping("/application-period")
    public Optional<ApplicationPeriod> currentApplicationPeriod() {
        return applicationPeriodRepository.findFirstByIsActiveTrueOrderByStartDateDesc();
    }

    @PostMapping("/application-period/start")
    public Map<String, String> startApplicationPeriod(@RequestBody Map<String, String> body) {
        ApplicationPeriod period = new ApplicationPeriod();
        period.setStartDate(java.time.LocalDate.parse(body.get("startDate")).atStartOfDay());
        period.setEndDate(java.time.LocalDate.parse(body.get("endDate")).atStartOfDay());
        if (period.getEndDate().isBefore(period.getStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before the start date.");
        }
        period.setIsActive(true);
        applicationPeriodRepository.save(period);
        return Map.of("message", "New application period started successfully!");
    }

    @PostMapping("/application-period/end")
    public Map<String, String> endApplicationPeriod() {
        int updated = jdbc.update("UPDATE application_period SET is_active = 0 WHERE is_active = 1");
        return Map.of("message", updated > 0 ? updated + " application period(s) ended successfully!" : "No active application period found to end.");
    }

    // ---------------------------------------------------------------- manage users

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT u.*, r.role_name FROM users u JOIN roles r ON u.role_id = r.role_id ORDER BY u.user_id");
        // Never send password hashes to the browser.
        users.forEach(u -> u.remove("password_hash"));
        return users;
    }

    @GetMapping("/roles")
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    @PostMapping("/users")
    public Map<String, String> createUser(@RequestBody Map<String, Object> body) {
        User user = new User();
        user.setUserId(String.valueOf(body.get("userId")));
        user.setName(String.valueOf(body.get("name")));
        user.setEmail(String.valueOf(body.get("email")));
        user.setPhone(String.valueOf(body.get("contact")));
        user.setRoleId(Integer.valueOf(String.valueOf(body.get("roleId"))));
        user.setStatus(String.valueOf(body.getOrDefault("status", "Active")));
        user.setRegion(String.valueOf(body.getOrDefault("region", "Jeddah")));
        user.setSex(String.valueOf(body.getOrDefault("sex", "M")));
        user.setPasswordHash(passwordEncoder.encode(String.valueOf(body.get("password"))));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("message", "User saved successfully!");
    }

    @PutMapping("/users/{userId}")
    public Map<String, String> updateUser(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (body.get("name") != null) user.setName(String.valueOf(body.get("name")));
        if (body.get("email") != null) user.setEmail(String.valueOf(body.get("email")));
        if (body.get("roleId") != null) user.setRoleId(Integer.valueOf(String.valueOf(body.get("roleId"))));
        if (body.get("status") != null) user.setStatus(String.valueOf(body.get("status")));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("message", "User updated successfully!");
    }

    @DeleteMapping("/users/{userId}")
    public Map<String, String> deleteUser(@PathVariable String userId) {
        userRepository.deleteById(userId);
        return Map.of("message", "User deleted successfully!");
    }

    // --------------------------------------------------------- system configuration

    @GetMapping("/system-configuration")
    public Map<String, Object> systemConfiguration() {
        List<Map<String, Object>> schedules = jdbc.queryForList(
                "SELECT ps.schedule_id, ps.amount, ps.year, ps.updated_at, u.name AS updated_by_name " +
                "FROM payment_schedules ps LEFT JOIN users u ON ps.updated_by = u.user_id ORDER BY ps.year DESC");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schedules", schedules);
        return result;
    }

    @PostMapping("/system-configuration")
    public Map<String, String> saveSchedule(@RequestBody Map<String, Object> body) {
        int year = Integer.parseInt(String.valueOf(body.get("year")));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        if (amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount cannot be negative.");
        }
        String userId = AuthUtil.currentUser().getUserId();
        PaymentSchedule schedule = paymentScheduleRepository.findByYear(year).orElseGet(PaymentSchedule::new);
        schedule.setYear(year);
        schedule.setAmount(amount);
        schedule.setStatus(1);
        schedule.setUpdatedBy(userId);
        schedule.setUpdatedAt(LocalDateTime.now());
        paymentScheduleRepository.save(schedule);
        return Map.of("message", "Payment amount for year " + year + " saved successfully.");
    }

    // ---------------------------------------------------------------- RCC centers

    @GetMapping("/rcc-centers")
    public List<RccCenter> listRccCenters() {
        return rccCenterRepository.findAll();
    }

    @PostMapping("/rcc-centers")
    public RccCenter saveRccCenter(@RequestBody RccCenter center) {
        if (center.getRccCenterId() != null) {
            rccCenterRepository.findById(center.getRccCenterId())
                    .ifPresent(existing -> center.setCreatedAt(existing.getCreatedAt()));
        }
        if (center.getCreatedAt() == null) center.setCreatedAt(LocalDateTime.now());
        center.setUpdatedAt(LocalDateTime.now());
        return rccCenterRepository.save(center);
    }

    @DeleteMapping("/rcc-centers/{id}")
    public void deleteRccCenter(@PathVariable Long id) {
        rccCenterRepository.deleteById(id);
    }

    // --------------------------------------------------------------------- courses

    @GetMapping("/courses")
    public List<Map<String, Object>> listCourses() {
        return jdbc.queryForList(
                "SELECT c.*, i.institution_name FROM courses c JOIN institutions i ON c.institution_id = i.institution_id");
    }

    @GetMapping("/courses/by-institution/{institutionId}")
    public List<Course> coursesByInstitution(@PathVariable String institutionId) {
        return courseRepository.findByInstitutionId(institutionId);
    }

    @PostMapping("/courses")
    public Course saveCourse(@RequestBody Course course) {
        return courseRepository.save(course);
    }

    @DeleteMapping("/courses/{id}")
    public void deleteCourse(@PathVariable Long id) {
        courseRepository.deleteById(id);
    }

    @GetMapping("/institutions")
    public List<Institution> listInstitutions() {
        return institutionRepository.findAll();
    }

    @PostMapping("/institutions")
    public Map<String, String> addInstitution(@RequestBody Map<String, Object> body) {
        String institutionId = body.get("institutionId") == null || String.valueOf(body.get("institutionId")).isBlank()
                ? "INST-" + (institutionRepository.count() + 101)
                : String.valueOf(body.get("institutionId"));
        Institution institution = new Institution();
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName(String.valueOf(body.get("institutionName")));
        institution.setAddress((String) body.get("address"));
        institution.setContactNumber((String) body.get("contactNumber"));
        institution.setEmail((String) body.get("email"));
        institution.setCreatedAt(LocalDateTime.now());
        institution.setUpdatedAt(LocalDateTime.now());
        institutionRepository.save(institution);
        return Map.of("message", "Institution added successfully with ID: " + institutionId);
    }

    // ------------------------------------------------------------------ applications

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('1','2','3','4')")
    public List<Map<String, Object>> manageApplications() {
        return jdbc.queryForList("""
            SELECT gd.*, s.status, s.comments, s.created_at AS status_date
            FROM grantee_details gd
            LEFT JOIN (
                SELECT grantee_detail_id, MAX(created_at) AS latest
                FROM application_status GROUP BY grantee_detail_id
            ) latest_status ON gd.grantee_detail_id = latest_status.grantee_detail_id
            LEFT JOIN application_status s ON s.grantee_detail_id = latest_status.grantee_detail_id
                AND s.created_at = latest_status.latest
            """);
    }

    private static final Set<String> VALID_STATUSES = Set.of(
            "draft", "submitted", "interviewing", "accepted", "rejected",
            "on hold", "provisional admission letter", "admitted");

    @PostMapping("/applications/{granteeDetailId}/status")
    @PreAuthorize("hasAnyRole('1','2','3','4')")
    public Map<String, String> updateApplicationStatus(@PathVariable Long granteeDetailId, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!VALID_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status selected!");
        }
        ApplicationStatus as = new ApplicationStatus();
        as.setGranteeDetailId(granteeDetailId);
        as.setStatus(status);
        as.setComments(body.get("comments"));
        as.setUpdatedBy(AuthUtil.currentUser().getUserId());
        as.setCreatedAt(LocalDateTime.now());
        as.setUpdatedAt(LocalDateTime.now());
        applicationStatusRepository.save(as);
        return Map.of("message", "Status updated successfully");
    }

    // ------------------------------------------------------------------ manage students

    @GetMapping("/manage-students")
    public List<Map<String, Object>> manageStudents() {
        return jdbc.queryForList("""
            SELECT u.user_id, u.name AS student_name, u.email AS student_email, u.phone AS student_phone, u.region,
                   sponsor.name AS sponsor_name, inst.institution_name, c.course_name,
                   sic.institution_id, sic.course_id
            FROM users u
            LEFT JOIN grantor_grantees gg ON u.user_id = gg.grantee_id
            LEFT JOIN users sponsor ON gg.grantor_id = sponsor.user_id
            LEFT JOIN student_institution_courses sic ON u.user_id = sic.user_id
            LEFT JOIN institutions inst ON sic.institution_id = inst.institution_id
            LEFT JOIN courses c ON sic.course_id = c.course_id
            WHERE u.role_id = 6
            """);
    }

    @PostMapping("/manage-students/assign")
    public Map<String, String> assignStudentCourse(@RequestBody Map<String, Object> body) {
        String userId = String.valueOf(body.get("userId"));
        String institutionId = String.valueOf(body.get("institutionId"));
        Long courseId = Long.valueOf(String.valueOf(body.get("courseId")));

        Long valid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE institution_id = ? AND course_id = ?", Long.class, institutionId, courseId);
        if (valid == null || valid == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid course-institution combination");
        }
        jdbc.update("""
            INSERT INTO student_institution_courses (user_id, institution_id, course_id, assigned_by, assigned_at)
            VALUES (?, ?, ?, ?, NOW())
            ON DUPLICATE KEY UPDATE institution_id = VALUES(institution_id), course_id = VALUES(course_id),
                assigned_by = VALUES(assigned_by), assigned_at = NOW()
            """, userId, institutionId, courseId, AuthUtil.currentUser().getUserId());
        return Map.of("message", "Assignment updated successfully!");
    }

    // --------------------------------------------------------------- sponsorships

    @GetMapping("/sponsorships")
    public List<Map<String, Object>> manageSponsorships() {
        return jdbc.queryForList("""
            SELECT u.user_id, MAX(u.name) AS name, MAX(u.email) AS email, MAX(u.phone) AS phone,
                   MAX(u.status) AS status, MAX(u.region) AS region, MAX(r.role_name) AS role_name,
                   GROUP_CONCAT(sr.reference_id SEPARATOR ', ') AS all_references
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            LEFT JOIN sponsor_references sr ON u.user_id = sr.user_id
            WHERE u.role_id IN (3,4,5) AND u.status = 'active'
            GROUP BY u.user_id ORDER BY name ASC
            """);
    }

    @GetMapping("/sponsorships/{userId}/map")
    public Map<String, Object> sponsorMappingScreen(@PathVariable String userId) {
        Map<String, Object> sponsor = jdbc.queryForMap(
                "SELECT user_id, name, email, region FROM users WHERE user_id = ?", userId);
        List<Map<String, Object>> references = jdbc.queryForList(
                "SELECT reference_id, sponsor_year, chapter FROM sponsor_references WHERE user_id = ?", userId);
        List<String> refIds = references.stream().map(r -> String.valueOf(r.get("reference_id"))).toList();

        List<Map<String, Object>> mapped = refIds.isEmpty() ? List.of() : jdbc.queryForList(
                "SELECT u.user_id, u.name, u.email, u.phone, u.region, MAX(gg.grantor_id) AS linked_ref_id " +
                "FROM users u JOIN grantor_grantees gg ON u.user_id = gg.grantee_id " +
                "WHERE gg.grantor_id IN (" + placeholders(refIds.size()) + ") AND u.status = 'active' " +
                "GROUP BY u.user_id ORDER BY u.name ASC", refIds.toArray());

        List<Object> availableParams = new ArrayList<>(refIds);
        String notIn = refIds.isEmpty() ? "''" : placeholders(refIds.size());
        List<Map<String, Object>> available = jdbc.queryForList("""
            SELECT u.user_id, u.name, u.email, u.phone, u.region,
                   MAX(actual_sponsor.name) AS current_sponsor_name, MAX(gg.grantor_id) AS current_ref_id
            FROM users u
            LEFT JOIN grantor_grantees gg ON u.user_id = gg.grantee_id
            LEFT JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id
            LEFT JOIN users actual_sponsor ON sr.user_id = actual_sponsor.user_id
            WHERE u.role_id = 6 AND u.status = 'active'
              AND (u.user_id NOT IN (SELECT grantee_id FROM grantor_grantees WHERE grantor_id IN (""" + notIn + """
            )))
            GROUP BY u.user_id ORDER BY u.name ASC
            """, availableParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sponsor", sponsor);
        result.put("references", references);
        result.put("mappedStudents", mapped);
        result.put("availableStudents", available);
        return result;
    }

    @PostMapping("/sponsorships/{userId}/map")
    public Map<String, String> mapStudentsToReference(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> studentIds = (List<String>) body.get("studentIds");
        String targetRefId = String.valueOf(body.get("targetReferenceId"));
        if (targetRefId == null || targetRefId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select a Reference ID for assignment.");
        }
        for (String studentId : studentIds) {
            jdbc.update("""
                INSERT INTO grantor_grantees (grantee_id, grantor_id, status, created_at)
                VALUES (?, ?, 'Accepted', NOW())
                ON DUPLICATE KEY UPDATE grantor_id = VALUES(grantor_id), updated_at = NOW()
                """, studentId, targetRefId);
        }
        return Map.of("message", "Students successfully mapped to Reference " + targetRefId + "!");
    }

    @GetMapping("/sponsorship-lookup")
    public Map<String, Object> sponsorshipLookup(@RequestParam String refId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id FROM sponsor_references WHERE reference_id = ? LIMIT 1", refId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No sponsor found for that Reference ID.");
        }
        return Map.of("userId", rows.get(0).get("user_id"));
    }

    // ------------------------------------------------------------- student directory

    @GetMapping("/students")
    public Map<String, Object> listStudents(@RequestParam(defaultValue = "0") int start,
                                             @RequestParam(defaultValue = "10") int length,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) String institutionId,
                                             @RequestParam(required = false) Long courseId) {
        StringBuilder body = new StringBuilder("""
            FROM users u
            LEFT JOIN grantee_details gd ON u.user_id = gd.user_id
            LEFT JOIN grantor_grantees gg ON u.user_id = gg.grantee_id
            LEFT JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id
            LEFT JOIN users sponsor ON sr.user_id = sponsor.user_id
            LEFT JOIN student_institution_courses sic ON u.user_id = sic.user_id
            LEFT JOIN institutions inst ON sic.institution_id = inst.institution_id
            LEFT JOIN courses c ON sic.course_id = c.course_id
            WHERE u.role_id = 6
            """);
        List<Object> params = new ArrayList<>();
        if (institutionId != null) { body.append(" AND sic.institution_id = ?"); params.add(institutionId); }
        if (courseId != null) { body.append(" AND sic.course_id = ?"); params.add(courseId); }
        if (search != null && !search.isBlank()) {
            body.append(" AND (u.name LIKE ? OR u.email LIKE ? OR u.user_id LIKE ? OR sponsor.name LIKE ? OR sr.reference_id LIKE ?)");
            String w = "%" + search + "%";
            params.addAll(List.of(w, w, w, w, w));
        }

        long filtered = jdbc.queryForObject("SELECT COUNT(DISTINCT u.user_id) " + body, Long.class, params.toArray());
        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM users WHERE role_id = 6", Long.class);

        List<Object> finalParams = new ArrayList<>(params);
        finalParams.add(start);
        finalParams.add(length);
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT u.user_id, u.name, u.email, u.phone, u.region, u.status,
                   MAX(sr.reference_id) AS sponsor_id, MAX(sponsor.name) AS sponsor_name,
                   MAX(inst.institution_name) AS institution_name, MAX(c.course_name) AS course_name
            """ + body + " GROUP BY u.user_id ORDER BY u.user_id DESC LIMIT ?, ?", finalParams.toArray());

        return Map.of("recordsTotal", total, "recordsFiltered", filtered, "data", data);
    }

    @GetMapping("/students/{userId}")
    public Map<String, Object> studentDetails(@PathVariable String userId) {
        Map<String, Object> profile;
        try {
            profile = jdbc.queryForMap("""
                SELECT u.user_id, u.name AS user_real_name, u.email, u.phone, u.status, u.region, u.year, gd.*
                FROM users u LEFT JOIN grantee_details gd ON u.user_id = gd.user_id WHERE u.user_id = ?
                """, userId);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Student not found");
        }

        // gd.* can overwrite these with NULLs when the student has no grantee_details row.
        profile = new LinkedHashMap<>(profile);
        profile.put("user_id", userId);
        profile.put("name", profile.get("user_real_name"));
        List<java.math.BigDecimal> scheduleAmount = profile.get("year") == null ? List.of() : jdbc.queryForList(
                "SELECT amount FROM payment_schedules WHERE year = ? AND status = 1 LIMIT 1", java.math.BigDecimal.class, profile.get("year"));
        profile.put("annual_schedule_amount", scheduleAmount.isEmpty() || scheduleAmount.get(0) == null ? 0 : scheduleAmount.get(0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("bank", firstOrNull(jdbc.queryForList("SELECT * FROM bank_details WHERE user_id = ?", userId)));
        result.put("course", firstOrNull(jdbc.queryForList("""
            SELECT sic.assigned_at, sic.institution_id, sic.course_id, c.course_name, c.number_of_semesters, i.institution_name
            FROM student_institution_courses sic
            JOIN courses c ON sic.course_id = c.course_id
            JOIN institutions i ON sic.institution_id = i.institution_id
            WHERE sic.user_id = ?
            """, userId)));
        result.put("sponsor", firstOrNull(jdbc.queryForList("""
            SELECT u.user_id, u.name, u.email, sr.reference_id
            FROM grantor_grantees gg
            JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id
            JOIN users u ON sr.user_id = u.user_id
            WHERE gg.grantee_id = ?
            """, userId)));
        result.put("payments", jdbc.queryForList("""
            SELECT p.*, u.name AS grantor_name FROM payments p LEFT JOIN users u ON p.grantor_id = u.user_id
            WHERE p.grantee_id = ? ORDER BY p.payment_date DESC
            """, userId));
        result.put("documents", jdbc.queryForList(
                "SELECT * FROM student_progress WHERE grantee_id = ? ORDER BY created_at DESC", userId));
        return result;
    }

    @PutMapping("/students/{userId}")
    public Map<String, String> updateStudent(@PathVariable String userId, @RequestBody Map<String, Object> data) {
        List<Object> userParams = new ArrayList<>();
        StringBuilder userSet = new StringBuilder();
        for (String field : List.of("name", "email", "phone", "region", "status")) {
            if (data.get(field) != null) {
                if (!userSet.isEmpty()) userSet.append(", ");
                userSet.append(field).append(" = ?");
                userParams.add(data.get(field));
            }
        }
        if (!userParams.isEmpty()) {
            userParams.add(userId);
            jdbc.update("UPDATE users SET " + userSet + ", updated_at = NOW() WHERE user_id = ?", userParams.toArray());
        }

        List<Object> gdParams = new ArrayList<>();
        StringBuilder gdSet = new StringBuilder();
        for (String field : List.of("father_name", "mother_name", "address", "father_mobile", "mother_mobile")) {
            if (data.get(toCamel(field)) != null) {
                if (!gdSet.isEmpty()) gdSet.append(", ");
                gdSet.append(field).append(" = ?");
                gdParams.add(data.get(toCamel(field)));
            }
        }
        if (!gdParams.isEmpty()) {
            gdParams.add(userId);
            jdbc.update("UPDATE grantee_details SET " + gdSet + ", updated_at = NOW() WHERE user_id = ?", gdParams.toArray());
        }

        if (data.get("accountNumber") != null) {
            boolean exists = !jdbc.queryForList("SELECT bank_detail_id FROM bank_details WHERE user_id = ?", userId).isEmpty();
            if (exists) {
                jdbc.update("UPDATE bank_details SET bank_name=?, account_number=?, ifsc_code=?, account_name=? WHERE user_id=?",
                        data.get("bankName"), data.get("accountNumber"), data.get("ifscCode"), data.get("accountName"), userId);
            } else {
                Long nextId = jdbc.queryForObject("SELECT COALESCE(MAX(bank_detail_id),0)+1 FROM bank_details", Long.class);
                jdbc.update("INSERT INTO bank_details (bank_detail_id, user_id, bank_name, account_number, ifsc_code, account_name) VALUES (?,?,?,?,?,?)",
                        nextId, userId, data.get("bankName"), data.get("accountNumber"), data.get("ifscCode"), data.get("accountName"));
            }
        }

        if (data.get("institutionId") != null && data.get("courseId") != null) {
            jdbc.update("""
                INSERT INTO student_institution_courses (user_id, institution_id, course_id, assigned_by, assigned_at)
                VALUES (?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE institution_id=VALUES(institution_id), course_id=VALUES(course_id)
                """, userId, data.get("institutionId"), data.get("courseId"), AuthUtil.currentUser().getUserId());
        }

        return Map.of("message", "Successfully updated student record.");
    }

    @PostMapping("/students/{userId}/action")
    public Map<String, String> studentAction(@PathVariable String userId, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        switch (action) {
            case "deactivate" -> jdbc.update("UPDATE users SET status = 'Inactive' WHERE user_id = ?", userId);
            case "activate" -> jdbc.update("UPDATE users SET status = 'Active' WHERE user_id = ?", userId);
            case "unmap" -> jdbc.update("DELETE FROM grantor_grantees WHERE grantee_id = ?", userId);
            case "delete" -> {
                long payCount = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE grantee_id = ?", Long.class, userId);
                if (payCount > 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Cannot delete student. " + payCount + " payment records exist. Deactivate instead.");
                }
                jdbc.update("DELETE FROM grantor_grantees WHERE grantee_id = ?", userId);
                jdbc.update("DELETE FROM student_institution_courses WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM bank_details WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM grantee_details WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid action");
        }
        return Map.of("message", "Done");
    }

    // --------------------------------------------------------------- bulk uploads

    @PostMapping(value = "/students/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> bulkUploadStudents(@RequestParam MultipartFile file) throws IOException {
        int success = 0;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true)
                .build().parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            for (var row : parser) {
                try {
                    Map<String, String> r = normalizeHeaders(row, parser.getHeaderNames());
                    String uId = r.getOrDefault("studentreference", "").trim();
                    if (uId.isEmpty()) continue;
                    String name = r.getOrDefault("studentname", "").trim();
                    String email = r.getOrDefault("email", uId + "@rahbar.com");
                    String phone = r.getOrDefault("mobilestudent", "");

                    boolean exists = !jdbc.queryForList("SELECT user_id FROM users WHERE user_id = ?", uId).isEmpty();
                    if (exists) {
                        jdbc.update("UPDATE users SET name=?, email=?, phone=?, updated_at=NOW() WHERE user_id=?", name, email, phone, uId);
                    } else {
                        jdbc.update("""
                            INSERT INTO users (user_id, name, email, sex, phone, role_id, status, password_hash, created_at, updated_at)
                            VALUES (?, ?, ?, 'M', ?, 6, 'Active', ?, NOW(), NOW())
                            """, uId, name, email, phone, passwordEncoder.encode("hello"));
                    }

                    jdbc.update("""
                        INSERT INTO grantee_details (user_id, name, father_name, address, course_applied, rcc_name, father_mobile, mother_mobile, student_mobile, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                        ON DUPLICATE KEY UPDATE name=VALUES(name), father_name=VALUES(father_name), address=VALUES(address), course_applied=VALUES(course_applied), updated_at=NOW()
                        """, uId, name, r.get("fathername"), r.get("address"), r.get("course(branch)"), r.get("rccnon-rcc"), r.get("mobile-1"), r.get("mobile-2"), phone);

                    success++;
                } catch (Exception rowEx) {
                    // Skip bad rows, mirroring the original's per-row try/except.
                }
            }
        }
        return Map.of("message", "Success! Processed " + success + " unique students.");
    }

    /**
     * Port of admin.bulk_upload_sponsors: merges sponsors by email / phone / name+chapter,
     * writes their commitment references and maps the "Student Assigned" IDs to each reference.
     */
    @PostMapping(value = "/sponsors/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> bulkUploadSponsors(@RequestParam MultipartFile file) throws IOException {
        byte[] raw = file.getBytes();
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.contains("\uFFFD")) text = new String(raw, StandardCharsets.ISO_8859_1); // latin-1 fallback, as in Flask
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        int success = 0;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).setAllowMissingColumnNames(true)
                .build().parse(new java.io.StringReader(text))) {
            for (var row : parser) {
                try {
                    Map<String, String> r = new HashMap<>();
                    for (String h : parser.getHeaderNames()) {
                        String key = h.trim().toLowerCase().replace(" ", "").replace("_", "");
                        r.put(key, row.isSet(h) ? blankToNull(row.get(h)) : null);
                    }
                    String refId = r.get("sponsorreference");
                    if (refId == null) continue;
                    String name = Objects.requireNonNullElse(r.get("sponsorname"), "");
                    String email = r.get("sponsoremail");
                    String mobile1 = r.get("sponsormobile1");
                    String chapter = Objects.requireNonNullElse(r.get("sponsorchapter"), "General");

                    String userId = null;
                    if (email != null) userId = firstUserId("SELECT user_id FROM users WHERE email = ? AND role_id IN (3,4,5) LIMIT 1", email);
                    if (userId == null && mobile1 != null) userId = firstUserId("SELECT user_id FROM users WHERE phone = ? AND role_id IN (3,4,5) LIMIT 1", mobile1);
                    if (userId == null && !name.isEmpty()) userId = firstUserId("SELECT user_id FROM users WHERE name = ? AND region = ? AND role_id IN (3,4,5) LIMIT 1", name, chapter);

                    if (userId == null) {
                        userId = "USR-" + refId;
                        jdbc.update("""
                            INSERT INTO users (user_id, name, email, phone, role_id, status, password_hash, region, created_at, updated_at)
                            VALUES (?, ?, ?, ?, 5, 'active', ?, ?, NOW(), NOW())
                            """, userId, name, email, mobile1, passwordEncoder.encode("hello"), chapter);
                    } else {
                        jdbc.update("UPDATE users SET updated_at = NOW() WHERE user_id = ?", userId);
                    }

                    String months = r.get("paymentnumberofmonths");
                    jdbc.update("""
                        REPLACE INTO sponsor_references (
                            reference_id, user_id, sponsor_year, chapter, referral,
                            installment_date, payment_months, confirm_credit_date,
                            special_demand, remarks, mobile_1, mobile_2, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                        """, refId, userId, r.get("sponsoryear"), chapter, r.get("sponsorreferal"),
                            safeDate(r.get("datetransf1stinstallment")), months == null ? "0" : months,
                            safeDate(r.get("confirmcreditdate")), r.get("specialdemand"), r.get("remarks"),
                            mobile1, r.get("sponsormobile2"));

                    String assigned = r.get("studentassigned");
                    if (assigned != null) {
                        for (String stuId : assigned.split(",")) {
                            if (stuId.isBlank()) continue;
                            jdbc.update("""
                                INSERT INTO grantor_grantees (grantor_id, grantee_id, status, created_at)
                                VALUES (?, ?, 'Accepted', NOW())
                                ON DUPLICATE KEY UPDATE updated_at = NOW()
                                """, refId, stuId.trim());
                        }
                    }
                    success++;
                } catch (Exception rowEx) {
                    // Skip bad rows, as the Flask version did.
                }
            }
        }
        return Map.of("message", "Success! Processed " + success + " rows. Sponsors merged and students mapped.");
    }

    private String firstUserId(String sql, Object... args) {
        List<String> ids = jdbc.queryForList(sql, String.class, args);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() || t.equalsIgnoreCase("nan") || t.equalsIgnoreCase("none") ? null : t;
    }

    /** Lenient date parsing (pandas.to_datetime did this in Flask); returns yyyy-MM-dd or null. */
    private static String safeDate(String v) {
        if (v == null) return null;
        String t = v.trim().split("[ T]")[0];
        for (String pattern : List.of("yyyy-MM-dd", "d/M/yyyy", "M/d/yyyy", "d-M-yyyy", "d.M.yyyy", "yyyy/M/d", "d-MMM-yyyy", "d-MMM-yy", "M/d/yy")) {
            try {
                return java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).toString();
            } catch (Exception ignored) { }
        }
        return null;
    }

    @PostMapping("/students/manual-add")
    public Map<String, String> manualAddStudent(@RequestBody Map<String, Object> body) {
        String uId = String.valueOf(body.get("userId"));
        String name = String.valueOf(body.get("name"));
        String email = body.get("email") != null ? String.valueOf(body.get("email")) : uId + "@rahbar.com";

        jdbc.update("""
            INSERT INTO users (user_id, name, email, sex, phone, role_id, status, password_hash, year, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 6, 'Active', ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE name=VALUES(name), email=VALUES(email), phone=VALUES(phone), year=VALUES(year), updated_at=NOW()
            """, uId, name, email, body.get("sex"), body.get("phone"), passwordEncoder.encode("hello"), body.get("year"));

        jdbc.update("""
            INSERT INTO grantee_details (user_id, name, father_name, mother_name, address, course_applied, rcc_name, father_mobile, mother_mobile, student_mobile, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE father_name=VALUES(father_name), mother_name=VALUES(mother_name), address=VALUES(address), course_applied=VALUES(course_applied), rcc_name=VALUES(rcc_name), updated_at=NOW()
            """, uId, name, body.get("fatherName"), body.get("motherName"), body.get("address"), body.get("courseName"),
                body.get("rccName"), body.get("fatherMobile"), body.get("motherMobile"), body.get("phone"));

        // Bank details (Flask: only when a bank name is given; account name = student name)
        Object bankName = body.get("bankName");
        if (bankName != null && !String.valueOf(bankName).isBlank()) {
            boolean exists = !jdbc.queryForList("SELECT bank_detail_id FROM bank_details WHERE user_id = ?", uId).isEmpty();
            if (exists) {
                jdbc.update("UPDATE bank_details SET bank_name=?, account_number=?, ifsc_code=?, account_name=? WHERE user_id=?",
                        bankName, body.get("accountNumber"), body.get("ifscCode"), name, uId);
            } else {
                Long nextId = jdbc.queryForObject("SELECT COALESCE(MAX(bank_detail_id),0)+1 FROM bank_details", Long.class);
                jdbc.update("INSERT INTO bank_details (bank_detail_id, user_id, bank_name, account_number, ifsc_code, account_name) VALUES (?,?,?,?,?,?)",
                        nextId, uId, bankName, body.get("accountNumber"), body.get("ifscCode"), name);
            }
        }

        // Institution & course
        Object instId = body.get("institutionId");
        Object courseId = body.get("courseId");
        if (instId != null && courseId != null && !String.valueOf(instId).isBlank() && !String.valueOf(courseId).isBlank()) {
            jdbc.update("""
                INSERT INTO student_institution_courses (user_id, institution_id, course_id, assigned_by, assigned_at)
                VALUES (?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE institution_id=VALUES(institution_id), course_id=VALUES(course_id)
                """, uId, instId, courseId, AuthUtil.currentUser().getUserId());
        }

        return Map.of("message", "Student " + uId + " registered successfully!");
    }

    @GetMapping("/sponsors/{userId}")
    public Map<String, Object> sponsorDetails(@PathVariable String userId) {
        Map<String, Object> profile;
        try {
            profile = jdbc.queryForMap("SELECT user_id, name, email, phone, region, status FROM users WHERE user_id = ?", userId);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found");
        }
        List<Map<String, Object>> references = jdbc.queryForList("SELECT * FROM sponsor_references WHERE user_id = ?", userId);
        return Map.of("profile", profile, "references", references);
    }

    @PutMapping("/sponsors/{userId}")
    public Map<String, String> updateSponsor(@PathVariable String userId, @RequestBody Map<String, Object> data) {
        jdbc.update("UPDATE users SET name=?, email=?, phone=?, region=? WHERE user_id=?",
                data.get("name"), data.get("email"), data.get("phone"), data.get("region"), userId);
        return Map.of("message", "Sponsor profile updated!");
    }

    // ------------------------------------------------------------------ payments

    @PostMapping(value = "/payments/record", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> recordPayment(@RequestParam String actionType,
                                              @RequestParam(required = false) Long paymentId,
                                              @RequestParam(required = false) String granteeId,
                                              @RequestParam(required = false) BigDecimal amount,
                                              @RequestParam(required = false) String paymentDate,
                                              @RequestParam(defaultValue = "Paid") String status,
                                              @RequestParam(required = false) MultipartFile receipt) {
        String receiptPath = null;
        if (receipt != null && !receipt.isEmpty()) {
            String filename = "admin_pay_" + granteeId + "_" + System.currentTimeMillis() + "_" + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
            receiptPath = fileStorageService.store(receipt, filename);
        }
        String userId = AuthUtil.currentUser().getUserId();

        if ("create".equals(actionType)) {
            List<Map<String, Object>> mapping = jdbc.queryForList(
                    "SELECT grantor_id FROM grantor_grantees WHERE grantee_id = ? LIMIT 1", granteeId);
            if (mapping.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This student is not assigned to any Sponsor Reference. Mapping required first.");
            }
            String referenceId = String.valueOf(mapping.get(0).get("grantor_id"));
            jdbc.update("""
                INSERT INTO payments (grantor_id, grantee_id, amount, payment_date, status, receipt_url, created_at, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), ?)
                """, referenceId, granteeId, amount, paymentDate, status, receiptPath, userId);
            return Map.of("message", "Payment recorded and linked to Sponsor Reference successfully.");
        } else {
            StringBuilder sql = new StringBuilder("UPDATE payments SET amount=?, payment_date=?, status=?, updated_at=NOW(), updated_by=?");
            List<Object> params = new ArrayList<>(List.of(amount, paymentDate, status, userId));
            if (receiptPath != null) { sql.append(", receipt_url=?"); params.add(receiptPath); }
            sql.append(" WHERE payment_id=?");
            params.add(paymentId);
            jdbc.update(sql.toString(), params.toArray());
            return Map.of("message", "Payment updated successfully.");
        }
    }

    // ------------------------------------------------------------------- reports

    @GetMapping("/reports/{type}")
    public ResponseEntity<ByteArrayResource> downloadReport(@PathVariable String type,
                                                              @RequestParam(defaultValue = "csv") String format) {
        List<Map<String, Object>> data;
        String filename;
        switch (type) {
            case "applications" -> {
                data = jdbc.queryForList("""
                    SELECT gd.grantee_detail_id, gd.name, gd.father_name, gd.rcc_name, gd.course_applied,
                           sponsor_user.name AS assigned_sponsor_name, sr.reference_id AS assigned_reference_id
                    FROM grantee_details gd
                    LEFT JOIN grantor_grantees gg ON gd.user_id = gg.grantee_id
                    LEFT JOIN sponsor_references sr ON gg.grantor_id = sr.reference_id
                    LEFT JOIN users sponsor_user ON sr.user_id = sponsor_user.user_id
                    """);
                filename = "applications_report";
            }
            case "payments" -> {
                data = jdbc.queryForList("""
                    SELECT p.payment_id, grantee_user.name AS grantee_name, sponsor_user.name AS grantor_name,
                           p.grantor_id AS grantor_reference_id, p.amount, p.status AS payment_status, p.payment_date
                    FROM payments p
                    LEFT JOIN users grantee_user ON p.grantee_id = grantee_user.user_id
                    LEFT JOIN sponsor_references sr ON p.grantor_id = sr.reference_id
                    LEFT JOIN users sponsor_user ON sr.user_id = sponsor_user.user_id
                    """);
                filename = "payments_report";
            }
            case "sponsors_convenors" -> {
                data = jdbc.queryForList("SELECT * FROM users WHERE role_id IN (4,5)");
                filename = "sponsors_convenors_report";
            }
            case "grantees" -> {
                data = jdbc.queryForList("SELECT * FROM users WHERE role_id = 6");
                filename = "grantees_report";
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type selected.");
        }
        if (data.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No data available for " + type + " report.");
        }

        byte[] bytes;
        MediaType mediaType;
        try {
            if ("pdf".equals(format)) {
                bytes = ReportUtil.toPdf(data, filename.replace('_', ' ').toUpperCase());
                mediaType = MediaType.APPLICATION_PDF;
                filename += ".pdf";
            } else if ("excel".equals(format)) {
                bytes = ReportUtil.toExcel(data);
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                filename += ".xlsx";
            } else {
                bytes = ReportUtil.toCsv(data);
                mediaType = MediaType.parseMediaType("text/csv");
                filename += ".csv";
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build report: " + e.getMessage());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(bytes));
    }

    // ------------------------------------------------------------------- helpers

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static Map<String, Object> firstOrNull(List<Map<String, Object>> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private static String toCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') { upperNext = true; continue; }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }

    private static Map<String, String> normalizeHeaders(org.apache.commons.csv.CSVRecord row, List<String> headers) {
        Map<String, String> result = new HashMap<>();
        for (String h : headers) {
            String key = h.trim().toLowerCase().replace(" ", "");
            result.put(key, row.isSet(h) ? row.get(h) : "");
        }
        return result;
    }
}
