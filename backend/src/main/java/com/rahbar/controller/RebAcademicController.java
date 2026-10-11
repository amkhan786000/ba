package com.rahbar.controller;

import com.rahbar.dto.RebPdf;
import com.rahbar.service.RebAttendanceService;
import com.rahbar.service.RebExamService;
import com.rahbar.service.RebReportCardService;
import com.rahbar.util.Rows;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Rahbar-e-Banaat phase 3: attendance, the holiday calendar, the grading scale, exams (date sheet, marks,
 * results, publishing), report cards and the annual result. Teachers with the "own REB classes" scope only reach
 * their classes and subjects (checked in the services).
 */
@RestController
@RequestMapping("/api/admin/reb")
@PreAuthorize("denyAll()")
public class RebAcademicController {

    private final RebAttendanceService attendance;
    private final RebExamService exams;
    private final RebReportCardService reportCards;

    public RebAcademicController(RebAttendanceService attendance, RebExamService exams, RebReportCardService reportCards) {
        this.attendance = attendance;
        this.exams = exams;
        this.reportCards = reportCards;
    }

    private static ResponseEntity<ByteArrayResource> pdf(RebPdf pdf) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename() + "\"")
                .body(new ByteArrayResource(pdf.bytes()));
    }

    // ---------------------------------------------------------------- attendance

    @GetMapping("/attendance")
    @PreAuthorize("hasAuthority('REB_ATTENDANCE:VIEW')")
    public Map<String, Object> day(@RequestParam Long sectionId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendance.day(sectionId, date);
    }

    /** Body: { sectionId, date, marks: [{ studentId, status, remark }] }. */
    @PutMapping("/attendance")
    @PreAuthorize("hasAuthority('REB_ATTENDANCE:EDIT')")
    public Map<String, Object> saveDay(@RequestBody Map<String, Object> body) {
        return attendance.save(body);
    }

    @GetMapping("/attendance/register")
    @PreAuthorize("hasAuthority('REB_ATTENDANCE:VIEW')")
    public Map<String, Object> register(@RequestParam Long sectionId, @RequestParam(required = false) String month) {
        return attendance.register(sectionId, month);
    }

    @GetMapping("/students/{id}/attendance")
    @PreAuthorize("hasAnyAuthority('REB_ATTENDANCE:VIEW', 'REB_STUDENTS:VIEW')")
    public Map<String, Object> studentAttendance(@PathVariable Long id, @RequestParam(required = false) Long sessionId) {
        return attendance.ofStudent(id, sessionId);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyAuthority('REB_ATTENDANCE:VIEW', 'REB_SETUP:VIEW')")
    public List<Map<String, Object>> calendar(@RequestParam(required = false) Long sessionId) {
        return attendance.calendar(sessionId);
    }

    /** Body: { sessionId, dateFrom, dateTo, title, kind HOLIDAY | EVENT | EXAM }. */
    @PostMapping("/calendar")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> addEvent(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Added to the calendar.", "data", Rows.of(attendance.saveEvent(null, body)));
    }

    @PutMapping("/calendar/{id}")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:EDIT')")
    public Map<String, Object> updateEvent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Calendar saved.", "data", Rows.of(attendance.saveEvent(id, body)));
    }

    @DeleteMapping("/calendar/{id}")
    @PreAuthorize("hasAnyAuthority('REB_SETUP:EDIT')")
    public Map<String, String> deleteEvent(@PathVariable Long id) {
        attendance.deleteEvent(id);
        return Map.of("message", "Removed from the calendar.");
    }

    // ---------------------------------------------------------------- grading scale and exams

    @GetMapping("/grade-scale")
    @PreAuthorize("hasAnyAuthority('REB_EXAMS:VIEW', 'REB_SETUP:VIEW')")
    public Map<String, Object> gradeScale(@RequestParam(required = false) Long sessionId) {
        return exams.gradeScale(sessionId);
    }

    /** Body: { sessionId, bands: [{ min, grade, remark, pass }] }. */
    @PutMapping("/grade-scale")
    @PreAuthorize("hasAuthority('REB_SETUP:EDIT')")
    public Map<String, String> saveGradeScale(@RequestBody Map<String, Object> body) {
        exams.saveGradeScale(body);
        return Map.of("message", "Grading scale saved.");
    }

    @GetMapping("/exams")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public List<Map<String, Object>> exams(@RequestParam(required = false) Long sessionId) {
        return exams.list(sessionId);
    }

    /** Body: { sessionId, name, kind, weightPercent, startDate, endDate }. Office only (not the "own classes" scope). */
    @PostMapping("/exams")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT') and !@rebScopeCheck.teacher()")
    public Map<String, Object> addExam(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Exam added. Now set its date sheet.", "data", Rows.of(exams.save(null, body)));
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT') and !@rebScopeCheck.teacher()")
    public Map<String, Object> updateExam(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Exam saved.", "data", Rows.of(exams.save(id, body)));
    }

    @DeleteMapping("/exams/{id}")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT') and !@rebScopeCheck.teacher()")
    public Map<String, String> deleteExam(@PathVariable Long id) {
        exams.delete(id);
        return Map.of("message", "Exam deleted.");
    }

    /** Body: { status: DRAFT | MARKS_ENTRY | PUBLISHED }. */
    @PostMapping("/exams/{id}/status")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT') and !@rebScopeCheck.teacher()")
    public Map<String, String> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", exams.setStatus(id, body));
    }

    @GetMapping("/exams/{id}/papers")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public Map<String, Object> dateSheet(@PathVariable Long id) {
        return exams.dateSheet(id);
    }

    /** Body: { papers: [{ classId, subjectId, included, examDate, maxMarks, passMarks }] }. */
    @PutMapping("/exams/{id}/papers")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT') and !@rebScopeCheck.teacher()")
    public Map<String, String> saveDateSheet(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        exams.saveDateSheet(id, body);
        return Map.of("message", "Date sheet saved.");
    }

    /** The papers the signed-in user enters marks for. */
    @GetMapping("/exams/{id}/my-papers")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public List<Map<String, Object>> myPapers(@PathVariable Long id) {
        return exams.myPapers(id);
    }

    @GetMapping("/papers/{id}/marks")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public Map<String, Object> marks(@PathVariable Long id) {
        return exams.marksSheet(id);
    }

    /** Body: { marks: [{ studentId, marks, absent, remark }] }. */
    @PutMapping("/papers/{id}/marks")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT')")
    public Map<String, String> saveMarks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", exams.saveMarks(id, body));
    }

    @GetMapping("/exams/{id}/results")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public Map<String, Object> results(@PathVariable Long id, @RequestParam Long sectionId) {
        return exams.results(id, sectionId);
    }

    /** Body: { remarks: [{ studentId, remark }] }. */
    @PutMapping("/exams/{id}/remarks")
    @PreAuthorize("hasAuthority('REB_EXAMS:EDIT')")
    public Map<String, String> saveRemarks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        exams.saveRemarks(id, body);
        return Map.of("message", "Remarks saved.");
    }

    @GetMapping("/exams/{id}/report-cards")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public ResponseEntity<ByteArrayResource> reportCards(@PathVariable Long id, @RequestParam Long sectionId) {
        return pdf(reportCards.forSection(id, sectionId));
    }

    @GetMapping("/students/{studentId}/report-card")
    @PreAuthorize("hasAnyAuthority('REB_EXAMS:VIEW', 'REB_STUDENTS:VIEW')")
    public ResponseEntity<ByteArrayResource> reportCard(@PathVariable Long studentId, @RequestParam Long examId) {
        return pdf(reportCards.forStudent(examId, studentId));
    }

    @GetMapping("/students/{id}/results")
    @PreAuthorize("hasAnyAuthority('REB_EXAMS:VIEW', 'REB_STUDENTS:VIEW')")
    public List<Map<String, Object>> studentResults(@PathVariable Long id) {
        return exams.ofStudent(id);
    }

    @GetMapping("/final-results")
    @PreAuthorize("hasAuthority('REB_EXAMS:VIEW')")
    public Map<String, Object> finalResults(@RequestParam(required = false) Long sessionId, @RequestParam Long sectionId) {
        return exams.finalResults(sessionId, sectionId);
    }
}
