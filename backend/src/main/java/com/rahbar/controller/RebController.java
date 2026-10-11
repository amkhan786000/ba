package com.rahbar.controller;

import com.rahbar.dto.RebPdf;
import com.rahbar.dto.RebReportDefinition;
import com.rahbar.service.RebCertificateService;
import com.rahbar.service.RebImportService;
import com.rahbar.service.RebReportService;
import com.rahbar.service.RebSetupService;
import com.rahbar.service.RebStaffService;
import com.rahbar.service.RebStudentService;
import com.rahbar.util.RebSupport;

import com.rahbar.service.ReportService;
import com.rahbar.util.Rows;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.rahbar.dto.ReportFile;

/**
 * Rahbar-e-Banaat (the girls' Madrasa-cum-School), phase 1: setup, staff, the girls, promotion, reports and the
 * dashboard. Every endpoint names the REB permission it needs (Roles & Permissions, "REB: ...").
 */
@RestController
@RequestMapping("/api/admin/reb")
@PreAuthorize("denyAll()")
public class RebController {

    private final RebSetupService setup;
    private final RebStaffService staff;
    private final RebStudentService students;
    private final RebImportService importer;
    private final RebCertificateService certificates;
    private final RebReportService reports;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.service.RebBillingService billing;

    public RebController(RebSetupService setup, RebStaffService staff, RebStudentService students, RebImportService importer,
                         RebCertificateService certificates, RebReportService reports) {
        this.setup = setup;
        this.staff = staff;
        this.students = students;
        this.importer = importer;
        this.certificates = certificates;
        this.reports = reports;
    }

    private static Map<String, Object> saved(String message, Object record) {
        return Map.of("message", message, "data", record instanceof Map<?, ?> ? record : Rows.of(record));
    }

    // ---------------------------------------------------------------- dashboard

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('REB_DASHBOARD:VIEW')")
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new java.util.LinkedHashMap<>(students.dashboard());
        out.put("fees", billing.monthSummary()); // null without "REB: fees"
        return out;
    }

    // ---------------------------------------------------------------- setup (sessions, classes, sections, subjects, curriculum, settings)

    /** Sessions are also needed by the students and staff screens to pick one. */
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:VIEW', 'REB_STUDENTS:VIEW', 'REB_STAFF:VIEW')")
    public List<Map<String, Object>> sessions() {
        return setup.sessions();
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> addSession(@RequestBody Map<String, Object> body) {
        return saved("Session added.", setup.saveSession(null, body));
    }

    @PutMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> updateSession(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Session saved.", setup.saveSession(id, body));
    }

    @PostMapping("/sessions/{id}/current")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> makeCurrent(@PathVariable Long id) {
        setup.makeCurrent(id);
        return Map.of("message", "This is now the current session.");
    }

    @GetMapping("/classes")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:VIEW', 'REB_STUDENTS:VIEW')")
    public List<Map<String, Object>> classes() {
        return setup.classes();
    }

    @PostMapping("/classes")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> addClass(@RequestBody Map<String, Object> body) {
        return saved("Class added.", setup.saveClass(null, body));
    }

    @PutMapping("/classes/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> updateClass(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Class saved.", setup.saveClass(id, body));
    }

    @DeleteMapping("/classes/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> deleteClass(@PathVariable Long id) {
        setup.deleteClass(id);
        return Map.of("message", "Class deleted.");
    }

    @GetMapping("/sections")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:VIEW', 'REB_STUDENTS:VIEW', 'REB_STAFF:VIEW')")
    public List<Map<String, Object>> sections(@RequestParam(required = false) Long sessionId) {
        return setup.sections(sessionId);
    }

    @PostMapping("/sections")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> addSection(@RequestBody Map<String, Object> body) {
        return saved("Section added.", setup.saveSection(null, body));
    }

    @PutMapping("/sections/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> updateSection(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Section saved.", setup.saveSection(id, body));
    }

    @DeleteMapping("/sections/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> deleteSection(@PathVariable Long id) {
        setup.deleteSection(id);
        return Map.of("message", "Section deleted.");
    }

    @GetMapping("/subjects")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:VIEW', 'REB_STAFF:VIEW')")
    public List<Map<String, Object>> subjects() {
        return setup.subjects();
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> addSubject(@RequestBody Map<String, Object> body) {
        return saved("Subject added.", setup.saveSubject(null, body));
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> updateSubject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Subject saved.", setup.saveSubject(id, body));
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> deleteSubject(@PathVariable Long id) {
        setup.deleteSubject(id);
        return Map.of("message", "Subject deleted.");
    }

    @GetMapping("/class-subjects")
    @PreAuthorize("hasAuthority('REB_SETUP:VIEW')")
    public List<Map<String, Object>> curriculum(@RequestParam(required = false) Long sessionId, @RequestParam Long classId) {
        return setup.curriculum(sessionId, classId);
    }

    /** Body: { sessionId, classId, subjects: [{ subjectId, maxMarks, passMarks }] }. */
    @PutMapping("/class-subjects")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> saveCurriculum(@RequestBody Map<String, Object> body) {
        setup.saveCurriculum(body);
        return Map.of("message", "Subjects of the class saved.");
    }

    /** Body: { fromSessionId, toSessionId }. */
    @PostMapping("/class-subjects/copy")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> copyCurriculum(@RequestBody Map<String, Object> body) {
        int n = setup.copyCurriculum(RebSupport.id(body.get("fromSessionId")), RebSupport.id(body.get("toSessionId")));
        return Map.of("message", n == 0 ? "Nothing to copy: every class already has its subjects." : n + " class subjects copied.");
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('REB_SETUP:VIEW')")
    public List<Map<String, Object>> settings() {
        return setup.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> saveSettings(@RequestBody Map<String, Object> body) {
        setup.saveSettings(body);
        return Map.of("message", "Settings saved.");
    }

    // ---------------------------------------------------------------- staff

    @GetMapping("/staff")
    @PreAuthorize("hasAnyAuthority('REB_STAFF:VIEW', 'REB_SETUP:VIEW')")
    public List<Map<String, Object>> staff() {
        return staff.list();
    }

    @GetMapping("/staff/{id}")
    @PreAuthorize("hasAuthority('REB_STAFF:VIEW')")
    public Map<String, Object> staffMember(@PathVariable Long id) {
        return staff.get(id);
    }

    @PostMapping("/staff")
    @PreAuthorize("hasAuthority('REB_STAFF:EDIT')")
    public Map<String, Object> addStaff(@RequestBody Map<String, Object> body) {
        return saved("Staff member added.", staff.save(null, body));
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize("hasAuthority('REB_STAFF:EDIT')")
    public Map<String, Object> updateStaff(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Staff member saved.", staff.save(id, body));
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('REB_STAFF:VIEW')")
    public Map<String, Object> assignments(@RequestParam(required = false) Long sessionId) {
        return staff.assignments(sessionId);
    }

    /** Body: { sessionId, assignments: [{ sectionId, subjectId, staffId }] }. */
    @PutMapping("/assignments")
    @PreAuthorize("hasAuthority('REB_STAFF:EDIT')")
    public Map<String, String> saveAssignments(@RequestBody Map<String, Object> body) {
        staff.saveAssignments(body);
        return Map.of("message", "Teachers saved.");
    }

    // ---------------------------------------------------------------- students

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('REB_STUDENTS:VIEW')")
    public Map<String, Object> students(@RequestParam(required = false) Long sessionId, @RequestParam(required = false) Long classId,
                                        @RequestParam(required = false) Long sectionId, @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Boolean residential, @RequestParam(required = false) String feePlan,
                                        @RequestParam(required = false) String q) {
        return students.list(sessionId, classId, sectionId, status, residential, feePlan, q);
    }

    @PostMapping("/students")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> admit(@RequestBody Map<String, Object> body) {
        Long id = students.admit(body);
        return Map.of("message", "Admitted.", "id", id);
    }

    @GetMapping("/students/{id}")
    @PreAuthorize("hasAuthority('REB_STUDENTS:VIEW')")
    public Map<String, Object> student(@PathVariable Long id) {
        return students.get(id);
    }

    @PutMapping("/students/{id}")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, String> updateStudent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        students.update(id, body);
        return Map.of("message", "Saved.");
    }

    /** Body: { status: LEFT | PASSED_OUT | ACTIVE, date, reason }. */
    @PostMapping("/students/{id}/status")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, String> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        students.setStatus(id, body);
        return Map.of("message", "Status changed.");
    }

    /** Body: { sectionId, rollNo }. */
    @PutMapping("/students/{id}/enrollment")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, String> setEnrollment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        students.setEnrollment(id, body);
        return Map.of("message", "Class and roll number saved.");
    }

    @PostMapping("/students/{id}/guardians")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> addGuardian(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Guardian added.", students.addGuardian(id, body));
    }

    @PutMapping("/guardians/{id}")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> updateGuardian(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return saved("Guardian saved.", students.updateGuardian(id, body));
    }

    @DeleteMapping("/guardians/{id}")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, String> deleteGuardian(@PathVariable Long id) {
        students.deleteGuardian(id);
        return Map.of("message", "Guardian removed.");
    }

    @PostMapping(value = "/students/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('REB_STUDENT_PRIVATE:EDIT')")
    public Map<String, Object> addDocument(@PathVariable Long id, @RequestParam String kind, @RequestParam MultipartFile file) {
        return Map.of("message", "Document uploaded.", "data", students.addDocument(id, kind, file));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('REB_STUDENT_PRIVATE:EDIT')")
    public Map<String, String> deleteDocument(@PathVariable Long id) {
        students.deleteDocument(id);
        return Map.of("message", "Document removed.");
    }

    /** type: TC (transfer certificate) or BONAFIDE. */
    @GetMapping("/students/{id}/certificate")
    @PreAuthorize("hasAuthority('REB_STUDENTS:VIEW')")
    public ResponseEntity<ByteArrayResource> certificate(@PathVariable Long id, @RequestParam String type) {
        RebPdf pdf = certificates.certificate(id, type);
        return file(pdf.bytes(), MediaType.APPLICATION_PDF_VALUE, pdf.filename());
    }

    @GetMapping("/students/import/template")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public ResponseEntity<ByteArrayResource> importTemplate() {
        return file(RebImportService.TEMPLATE.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/csv", "reb_students_template.csv");
    }

    @PostMapping(value = "/students/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> importStudents(@RequestParam MultipartFile file) throws IOException {
        return importer.importCsv(file);
    }

    @GetMapping("/promotion")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> promotionPreview(@RequestParam Long fromSessionId, @RequestParam Long toSessionId) {
        return students.promotionPreview(fromSessionId, toSessionId);
    }

    /** Body: { fromSessionId, toSessionId, rows: [{ enrollmentId, action, targetSectionId }] }. */
    @PostMapping("/promotion")
    @PreAuthorize("hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> promote(@RequestBody Map<String, Object> body) {
        return students.promote(body);
    }

    // ---------------------------------------------------------------- reports

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('REB_REPORTS:VIEW')")
    public List<RebReportDefinition> reports() {
        return reports.catalog();
    }

    @GetMapping("/reports/{key}/data")
    @PreAuthorize("hasAuthority('REB_REPORTS:VIEW')")
    public Map<String, Object> reportData(@PathVariable String key,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.data(key, from, to);
    }

    @GetMapping("/reports/{key}")
    @PreAuthorize("hasAuthority('REB_REPORTS:VIEW')")
    public ResponseEntity<ByteArrayResource> report(@PathVariable String key, @RequestParam(defaultValue = "csv") String format,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportFile r = reports.build(key, format, from, to);
        return file(r.bytes(), r.contentType(), r.filename());
    }

    private static ResponseEntity<ByteArrayResource> file(byte[] bytes, String type, String name) {
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(new ByteArrayResource(bytes));
    }
}
