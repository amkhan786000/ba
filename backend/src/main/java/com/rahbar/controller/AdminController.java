package com.rahbar.controller;

import com.rahbar.util.Ids;
import com.rahbar.entity.*;
import com.rahbar.security.AuthUtil;
import com.rahbar.service.AdminService;
import com.rahbar.service.BulkUploadReport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mirrors routes/admin.py (role_id 1 = Super Admin, 2 = Application Administrator).
 * The Office Coordinator (role 8) shares a subset of these screens (see {@link #ADMIN_OR_OFFICE}).
 * All data access and business rules live in {@link AdminService}.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('1','2')")
public class AdminController {

    /** Office Coordinator (role 8) shares a subset of the admin screens: payment config, RCC centers,
     *  courses/institutions, sponsors (map students only, no contact info) and the student directory. */
    static final int OFFICE_COORDINATOR = 8;
    static final String ADMIN_OR_OFFICE = "hasAnyRole('1','2','8')";

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    private static boolean isOfficeCoordinator() {
        return Integer.valueOf(OFFICE_COORDINATOR).equals(AuthUtil.currentUser().getRoleId());
    }

    // ---------------------------------------------------------------- dashboard

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) Integer year) {
        return adminService.dashboard(year);
    }

    // ---------------------------------------------------------- application period

    @GetMapping("/application-period")
    public Optional<ApplicationPeriod> currentApplicationPeriod() {
        return adminService.currentApplicationPeriod();
    }

    @PostMapping("/application-period/start")
    public Map<String, String> startApplicationPeriod(@RequestBody Map<String, String> body) {
        adminService.startApplicationPeriod(body.get("startDate"), body.get("endDate"));
        return Map.of("message", "New application period started successfully!");
    }

    @PostMapping("/application-period/end")
    public Map<String, String> endApplicationPeriod() {
        int ended = adminService.endApplicationPeriods();
        return Map.of("message", ended > 0 ? ended + " application period(s) ended successfully!" : "No active application period found to end.");
    }

    // ---------------------------------------------------------------- manage users

    /** Paged: { data, total, page, size }. page is 1-based. */
    @GetMapping("/users")
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String email,
                                         @RequestParam(required = false) Integer roleId,
                                         @RequestParam(required = false) String status) {
        return adminService.listUsers(page, size, name, email, roleId, status);
    }

    @GetMapping("/users/{userId}")
    public Map<String, Object> getUser(@PathVariable Long userId) {
        return adminService.getUser(userId);
    }

    @PostMapping("/users")
    public Map<String, String> createUser(@RequestBody Map<String, Object> body) {
        adminService.createUser(body);
        return Map.of("message", "User saved successfully!");
    }

    @PutMapping("/users/{userId}")
    public Map<String, String> updateUser(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        adminService.updateUser(userId, body);
        return Map.of("message", "User updated successfully!");
    }

    // --------------------------------------------------------- system configuration

    @GetMapping("/system-configuration")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, Object> systemConfiguration() {
        return adminService.systemConfiguration();
    }

    @PostMapping("/system-configuration")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> saveSchedule(@RequestBody Map<String, Object> body) {
        int year = Integer.parseInt(String.valueOf(body.get("year")));
        adminService.saveSchedule(year, new BigDecimal(String.valueOf(body.get("amount"))));
        return Map.of("message", "Payment amount for year " + year + " saved successfully.");
    }

    // ---------------------------------------------------------------- RCC centers

    @GetMapping("/rcc-centers")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public List<RccCenter> listRccCenters() {
        return adminService.listRccCenters();
    }

    @PostMapping("/rcc-centers")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public RccCenter saveRccCenter(@RequestBody RccCenter center) {
        return adminService.saveRccCenter(center);
    }

    @DeleteMapping("/rcc-centers/{id}")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public void deleteRccCenter(@PathVariable Long id) {
        adminService.deleteRccCenter(id);
    }

    // --------------------------------------------------------------------- courses

    @GetMapping("/courses")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public List<Map<String, Object>> listCourses() {
        return adminService.listCourses();
    }

    @GetMapping("/courses/by-institution/{institutionId}")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public List<Course> coursesByInstitution(@PathVariable String institutionId) {
        return adminService.coursesByInstitution(institutionId);
    }

    @PostMapping("/courses")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Course saveCourse(@RequestBody Course course) {
        return adminService.saveCourse(course);
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public void deleteCourse(@PathVariable Long id) {
        adminService.deleteCourse(id);
    }

    @GetMapping("/institutions")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public List<Institution> listInstitutions() {
        return adminService.listInstitutions();
    }

    @PostMapping("/institutions")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> addInstitution(@RequestBody Map<String, Object> body) {
        String institutionId = adminService.addInstitution(body);
        return Map.of("message", "Institution added successfully with ID: " + institutionId);
    }

    // ------------------------------------------------------------------ applications

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('1','2','3','4')")
    public Map<String, Object> manageApplications(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String rcc) {
        return adminService.applications(page, size, name, status, rcc);
    }

    @PostMapping("/applications/{granteeDetailId}/status")
    @PreAuthorize("hasAnyRole('1','2','3','4')")
    public Map<String, String> updateApplicationStatus(@PathVariable Long granteeDetailId, @RequestBody Map<String, String> body) {
        adminService.updateApplicationStatus(granteeDetailId, body.get("status"), body.get("comments"));
        return Map.of("message", "Status updated successfully");
    }

    // ------------------------------------------------------------------ manage students

    @GetMapping("/manage-students")
    public List<Map<String, Object>> manageStudents() {
        return adminService.manageStudents();
    }

    @PostMapping("/manage-students/assign")
    public Map<String, String> assignStudentCourse(@RequestBody Map<String, Object> body) {
        adminService.assignStudentCourse(Ids.toLong(body.get("id")), String.valueOf(body.get("institutionId")),
                Long.valueOf(String.valueOf(body.get("courseId"))));
        return Map.of("message", "Assignment updated successfully!");
    }

    // --------------------------------------------------------------- sponsorships

    @GetMapping("/sponsorships")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public List<Map<String, Object>> manageSponsorships() {
        // Office coordinators may not see sponsor contact details.
        return adminService.sponsorships(isOfficeCoordinator());
    }

    @GetMapping("/sponsorships/{userId}/map")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, Object> sponsorMappingScreen(@PathVariable Long userId) {
        return adminService.sponsorMappingScreen(userId, isOfficeCoordinator());
    }

    @PostMapping("/sponsorships/{userId}/map")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> mapStudentsToSponsor(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        List<Long> studentIds = Ids.toLongs(body.get("studentIds"));
        adminService.mapStudentsToSponsor(userId, studentIds);
        return Map.of("message", studentIds.size() + " student(s) mapped to the sponsor successfully!");
    }

    // ------------------------------------------------------------- student directory

    @GetMapping("/students")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, Object> listStudents(@RequestParam(defaultValue = "0") int start,
                                            @RequestParam(defaultValue = "10") int length,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String institutionId,
                                            @RequestParam(required = false) Long courseId) {
        return adminService.listStudents(start, length, search, institutionId, courseId);
    }

    @GetMapping("/students/{userId}")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, Object> studentDetails(@PathVariable Long userId) {
        return adminService.studentDetails(userId);
    }

    @PutMapping("/students/{userId}")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> updateStudent(@PathVariable Long userId, @RequestBody Map<String, Object> data) {
        adminService.updateStudent(userId, data);
        return Map.of("message", "Successfully updated student record.");
    }

    @PostMapping("/students/{userId}/action")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> studentAction(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        adminService.studentAction(userId, body.get("action"));
        return Map.of("message", "Done");
    }

    // --------------------------------------------------------------- bulk uploads

    @PostMapping(value = "/students/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, Object> bulkUploadStudents(@RequestParam MultipartFile file) throws IOException {
        BulkUploadReport report = adminService.bulkUploadStudents(file);
        return Map.of("message", "Processed " + report.processed() + " student(s): " + report.toMap().get("created") + " added, "
                        + report.toMap().get("updated") + " updated, " + report.failedCount() + " failed.",
                "report", report.toMap());
    }

    @PostMapping(value = "/sponsors/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> bulkUploadSponsors(@RequestParam MultipartFile file) throws IOException {
        BulkUploadReport report = adminService.bulkUploadSponsors(file);
        return Map.of("message", "Processed " + report.processed() + " sponsor row(s): " + report.toMap().get("created") + " new, "
                        + report.toMap().get("updated") + " merged, " + report.toMap().get("mappings") + " student mapping(s), "
                        + report.failedCount() + " failed.",
                "report", report.toMap());
    }

    /** Empty CSV template (with one example row) for the student or sponsor bulk upload. */
    @GetMapping("/templates/{kind}")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public ResponseEntity<byte[]> csvTemplate(@PathVariable String kind) {
        byte[] bytes = adminService.csvTemplate(kind).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + "_template.csv\"")
                .body(bytes);
    }

    @PostMapping("/students/manual-add")
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> manualAddStudent(@RequestBody Map<String, Object> body) {
        String userId = adminService.manualAddStudent(body);
        return Map.of("message", "Student " + userId + " registered successfully!");
    }

    @GetMapping("/sponsors/{userId}")
    public Map<String, Object> sponsorDetails(@PathVariable Long userId) {
        return adminService.sponsorDetails(userId);
    }

    @PutMapping("/sponsors/{userId}")
    public Map<String, String> updateSponsor(@PathVariable Long userId, @RequestBody Map<String, Object> data) {
        adminService.updateSponsor(userId, data);
        return Map.of("message", "Sponsor profile updated!");
    }

    // ------------------------------------------------------------------ payments

    @PostMapping(value = "/payments/record", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ADMIN_OR_OFFICE)
    public Map<String, String> recordPayment(@RequestParam String actionType,
                                             @RequestParam(required = false) Long paymentId,
                                             @RequestParam(required = false) Long granteeId,
                                             @RequestParam(required = false) BigDecimal amount,
                                             @RequestParam(required = false) String paymentDate,
                                             @RequestParam(defaultValue = "Paid") String status,
                                             @RequestParam(required = false) MultipartFile receipt) {
        return Map.of("message", adminService.recordPayment(actionType, paymentId, granteeId, amount, paymentDate, status, receipt));
    }
}
