package com.rahbar.controller;

import com.rahbar.util.Ids;
import com.rahbar.entity.*;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
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
 * Admin screens. Each endpoint requires a permission of the user's role (e.g. "USERS:EDIT", see
 * {@link com.rahbar.security.Section}), managed in Admin > Roles. All data access and business rules live in
 * {@link AdminService}.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Sponsor contact details are only shown with the Sponsor details permission (see SponsorPrivacy). */
    private static boolean hideSponsorContacts() {
        return !Access.can(Section.SPONSOR_DETAILS, Section.Level.VIEW);
    }

    // ---------------------------------------------------------------- dashboard

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD:VIEW')")
    public Map<String, Object> dashboard(@RequestParam(required = false) Integer year) {
        return adminService.dashboard(year);
    }

    // ---------------------------------------------------------- application period

    @GetMapping("/application-period")
    @PreAuthorize("hasAuthority('APPLICATION_PERIOD:VIEW')")
    public Optional<ApplicationPeriod> currentApplicationPeriod() {
        return adminService.currentApplicationPeriod();
    }

    @PostMapping("/application-period/start")
    @PreAuthorize("hasAuthority('APPLICATION_PERIOD:EDIT')")
    public Map<String, String> startApplicationPeriod(@RequestBody Map<String, String> body) {
        adminService.startApplicationPeriod(body.get("startDate"), body.get("endDate"));
        return Map.of("message", "New application period started successfully!");
    }

    @PostMapping("/application-period/end")
    @PreAuthorize("hasAuthority('APPLICATION_PERIOD:EDIT')")
    public Map<String, String> endApplicationPeriod() {
        int ended = adminService.endApplicationPeriods();
        return Map.of("message", ended > 0 ? ended + " application period(s) ended successfully!" : "No active application period found to end.");
    }

    // ---------------------------------------------------------------- manage users

    /** Paged: { data, total, page, size }. page is 1-based. */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USERS:VIEW')")
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) String email,
                                         @RequestParam(required = false) Integer roleId,
                                         @RequestParam(required = false) String status) {
        return adminService.listUsers(page, size, name, email, roleId, status);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('USERS:VIEW')")
    public Map<String, Object> getUser(@PathVariable Long userId) {
        return adminService.getUser(userId);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USERS:EDIT')")
    public Map<String, String> createUser(@RequestBody Map<String, Object> body) {
        adminService.createUser(body);
        return Map.of("message", "User saved successfully!");
    }

    /** New temporary password, emailed to the user (returned instead when it can't be emailed). */
    @PostMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasAuthority('USERS:EDIT')")
    public Map<String, Object> resetUserPassword(@PathVariable Long userId) {
        return adminService.resetUserPassword(userId);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('USERS:EDIT')")
    public Map<String, String> updateUser(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        adminService.updateUser(userId, body);
        return Map.of("message", "User updated successfully!");
    }

    // --------------------------------------------------------- system configuration

    @GetMapping("/system-configuration")
    @PreAuthorize("hasAuthority('PAYMENT_CONFIG:VIEW')")
    public Map<String, Object> systemConfiguration() {
        return adminService.systemConfiguration();
    }

    @PostMapping("/system-configuration")
    @PreAuthorize("hasAuthority('PAYMENT_CONFIG:EDIT')")
    public Map<String, String> saveSchedule(@RequestBody Map<String, Object> body) {
        int year = Integer.parseInt(String.valueOf(body.get("year")));
        Object frequency = body.get("frequencyMonths");
        adminService.saveSchedule(year, new BigDecimal(String.valueOf(body.get("amount"))),
                frequency == null || String.valueOf(frequency).isBlank() ? null : Integer.valueOf(String.valueOf(frequency)));
        return Map.of("message", "Payment config for " + year + " saved. Unpaid installments of students from " + year + " were updated.");
    }

    // ---------------------------------------------------------------- RCC centers

    @GetMapping("/rcc-centers")
    @PreAuthorize("hasAuthority('RCC_CENTERS:VIEW') or hasAuthority('STUDENTS:VIEW') or hasAuthority('USERS:VIEW')")
    public List<RccCenter> listRccCenters() {
        return adminService.listRccCenters();
    }

    @PostMapping("/rcc-centers")
    @PreAuthorize("hasAuthority('RCC_CENTERS:EDIT')")
    public RccCenter saveRccCenter(@RequestBody RccCenter center) {
        return adminService.saveRccCenter(center);
    }

    @DeleteMapping("/rcc-centers/{id}")
    @PreAuthorize("hasAuthority('RCC_CENTERS:EDIT')")
    public void deleteRccCenter(@PathVariable Long id) {
        adminService.deleteRccCenter(id);
    }

    // --------------------------------------------------------------------- courses

    @GetMapping("/courses")
    @PreAuthorize("hasAuthority('COURSES:VIEW') or hasAuthority('STUDENTS:VIEW')")
    public List<Map<String, Object>> listCourses() {
        return adminService.listCourses();
    }

    @GetMapping("/courses/by-institution/{institutionId}")
    @PreAuthorize("hasAuthority('COURSES:VIEW') or hasAuthority('STUDENTS:VIEW')")
    public List<Course> coursesByInstitution(@PathVariable String institutionId) {
        return adminService.coursesByInstitution(institutionId);
    }

    @PostMapping("/courses")
    @PreAuthorize("hasAuthority('COURSES:EDIT')")
    public Course saveCourse(@RequestBody Course course) {
        return adminService.saveCourse(course);
    }

    @DeleteMapping("/courses/{id}")
    @PreAuthorize("hasAuthority('COURSES:EDIT')")
    public void deleteCourse(@PathVariable Long id) {
        adminService.deleteCourse(id);
    }

    @GetMapping("/institutions")
    @PreAuthorize("hasAuthority('COURSES:VIEW') or hasAuthority('STUDENTS:VIEW')")
    public List<Institution> listInstitutions() {
        return adminService.listInstitutions();
    }

    @PostMapping("/institutions")
    @PreAuthorize("hasAuthority('COURSES:EDIT')")
    public Map<String, String> addInstitution(@RequestBody Map<String, Object> body) {
        String institutionId = adminService.addInstitution(body);
        return Map.of("message", "Institution added successfully with ID: " + institutionId);
    }

    @GetMapping("/institutions/{institutionId}")
    @PreAuthorize("hasAuthority('COURSES:VIEW')")
    public Institution getInstitution(@PathVariable String institutionId) {
        return adminService.getInstitution(institutionId);
    }

    @PutMapping("/institutions/{institutionId}")
    @PreAuthorize("hasAuthority('COURSES:EDIT')")
    public Map<String, String> updateInstitution(@PathVariable String institutionId, @RequestBody Map<String, Object> body) {
        adminService.updateInstitution(institutionId, body);
        return Map.of("message", "Institution updated.");
    }

    @DeleteMapping("/institutions/{institutionId}")
    @PreAuthorize("hasAuthority('COURSES:EDIT')")
    public Map<String, String> deleteInstitution(@PathVariable String institutionId) {
        adminService.deleteInstitution(institutionId);
        return Map.of("message", "Institution deleted.");
    }

    // ------------------------------------------------------------------ applications

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW') or hasAnyRole('3','4')")
    public Map<String, Object> manageApplications(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String rcc) {
        return adminService.applications(page, size, name, status, rcc);
    }

    @PostMapping("/applications/{granteeDetailId}/status")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    public Map<String, String> updateApplicationStatus(@PathVariable Long granteeDetailId, @RequestBody Map<String, String> body) {
        adminService.updateApplicationStatus(granteeDetailId, body.get("status"), body.get("comments"));
        return Map.of("message", "Status updated successfully");
    }

    // ------------------------------------------------------------------ manage students

    @GetMapping("/manage-students")
    @PreAuthorize("hasAuthority('STUDENTS:VIEW')")
    public List<Map<String, Object>> manageStudents() {
        return adminService.manageStudents();
    }

    @PostMapping("/manage-students/assign")
    @PreAuthorize("hasAuthority('STUDENTS:EDIT')")
    public Map<String, String> assignStudentCourse(@RequestBody Map<String, Object> body) {
        adminService.assignStudentCourse(Ids.toLong(body.get("id")), String.valueOf(body.get("institutionId")),
                Long.valueOf(String.valueOf(body.get("courseId"))));
        return Map.of("message", "Assignment updated successfully!");
    }

    // --------------------------------------------------------------- sponsorships

    @GetMapping("/sponsorships")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW') or hasAuthority('STUDENTS:VIEW')")
    public List<Map<String, Object>> manageSponsorships() {
        // Office coordinators may not see sponsor contact details.
        return adminService.sponsorships(hideSponsorContacts());
    }

    @GetMapping("/sponsorships/{userId}/map")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public Map<String, Object> sponsorMappingScreen(@PathVariable Long userId) {
        return adminService.sponsorMappingScreen(userId, hideSponsorContacts());
    }

    @PostMapping("/sponsorships/{userId}/map")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, String> mapStudentsToSponsor(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        List<Long> studentIds = Ids.toLongs(body.get("studentIds"));
        adminService.mapStudentsToSponsor(userId, studentIds);
        return Map.of("message", studentIds.size() + " student(s) mapped to the sponsor successfully!");
    }

    // ------------------------------------------------------------- student directory

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('STUDENTS:VIEW')")
    public Map<String, Object> listStudents(@RequestParam(defaultValue = "0") int start,
                                            @RequestParam(defaultValue = "10") int length,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String institutionId,
                                            @RequestParam(required = false) Long courseId) {
        return adminService.listStudents(start, length, search, institutionId, courseId);
    }

    @GetMapping("/students/{userId}")
    @PreAuthorize("hasAuthority('STUDENTS:VIEW')")
    public Map<String, Object> studentDetails(@PathVariable Long userId) {
        return adminService.studentDetails(userId);
    }

    @PutMapping("/students/{userId}")
    @PreAuthorize("hasAuthority('STUDENTS:EDIT')")
    public Map<String, String> updateStudent(@PathVariable Long userId, @RequestBody Map<String, Object> data) {
        adminService.updateStudent(userId, data);
        return Map.of("message", "Successfully updated student record.");
    }

    /** Body: { "status": "STUDYING" | "ON_HOLD" | "GRADUATED" | "DROPPED_OUT", "date": "yyyy-MM-dd", "note": "..." }. */
    @PostMapping("/students/{userId}/study-status")
    @PreAuthorize("hasAuthority('STUDENTS:EDIT')")
    public Map<String, String> setStudyStatus(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        adminService.setStudyStatus(userId, body.get("status"), body.get("date"), body.get("note"));
        return Map.of("message", "Study status updated.");
    }

    @PostMapping("/students/{userId}/action")
    @PreAuthorize("hasAuthority('STUDENTS:EDIT')")
    public Map<String, String> studentAction(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        adminService.studentAction(userId, body.get("action"));
        return Map.of("message", "Done");
    }

    // --------------------------------------------------------------- bulk uploads

    @PostMapping(value = "/students/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('STUDENTS:EDIT')")
    public Map<String, Object> bulkUploadStudents(@RequestParam MultipartFile file) throws IOException {
        BulkUploadReport report = adminService.bulkUploadStudents(file);
        return Map.of("message", "Processed " + report.processed() + " student(s): " + report.toMap().get("created") + " added, "
                        + report.toMap().get("updated") + " updated, " + report.failedCount() + " failed.",
                "report", report.toMap());
    }

    @PostMapping(value = "/sponsors/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> bulkUploadSponsors(@RequestParam MultipartFile file) throws IOException {
        BulkUploadReport report = adminService.bulkUploadSponsors(file);
        return Map.of("message", "Processed " + report.processed() + " sponsor row(s): " + report.toMap().get("created") + " new, "
                        + report.toMap().get("updated") + " merged, " + report.toMap().get("mappings") + " student mapping(s), "
                        + report.failedCount() + " failed.",
                "report", report.toMap());
    }

    /** Empty CSV template (with one example row) for the student or sponsor bulk upload. */
    @GetMapping("/templates/{kind}")
    @PreAuthorize("hasAuthority('STUDENTS:EDIT') or hasAuthority('SPONSORSHIPS:EDIT')")
    public ResponseEntity<byte[]> csvTemplate(@PathVariable String kind) {
        byte[] bytes = adminService.csvTemplate(kind).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + "_template.csv\"")
                .body(bytes);
    }

    @PostMapping("/students/manual-add")
    @PreAuthorize("hasAuthority('STUDENTS:EDIT')")
    public Map<String, String> manualAddStudent(@RequestBody Map<String, Object> body) {
        String userId = adminService.manualAddStudent(body);
        return Map.of("message", "Student " + userId + " registered successfully!");
    }

    @GetMapping("/sponsors/{userId}")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public Map<String, Object> sponsorDetails(@PathVariable Long userId) {
        return adminService.sponsorDetails(userId);
    }

    @PutMapping("/sponsors/{userId}")
    @PreAuthorize("hasAuthority('SPONSOR_DETAILS:EDIT')")
    public Map<String, String> updateSponsor(@PathVariable Long userId, @RequestBody Map<String, Object> data) {
        adminService.updateSponsor(userId, data);
        return Map.of("message", "Sponsor profile updated!");
    }

    // ------------------------------------------------------------------ payments

    @PostMapping(value = "/payments/record", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('STUDENTS:EDIT') or hasAuthority('PAYMENT_RECORDS:EDIT')")
    public Map<String, String> recordPayment(@RequestParam String actionType,
                                             @RequestParam(required = false) Long installmentId,
                                             @RequestParam(required = false) Long paymentId,
                                             @RequestParam(required = false) Long granteeId,
                                             @RequestParam(required = false) BigDecimal amount,
                                             @RequestParam(required = false) String paymentDate,
                                             @RequestParam(defaultValue = "Paid") String status,
                                             @RequestParam(required = false) MultipartFile receipt) {
        return Map.of("message", adminService.recordPayment(actionType, paymentId, granteeId, amount, paymentDate, status, receipt, installmentId));
    }
}
