package com.rahbar.controller;

import com.rahbar.service.CoachingDashboardService;
import com.rahbar.service.CoachingExamService;
import com.rahbar.service.CoachingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Coaching Center (classes 7 to 10): students, teachers, subjects and exams. Every record is limited to the user's
 * centers: the office sees all, a coaching center coordinator manages their own, a coaching teacher sees their own.
 */
@RestController
@RequestMapping("/api/admin/coaching")
@PreAuthorize("denyAll()")
public class CoachingController {

    private final CoachingService coaching;
    private final CoachingExamService exams;
    private final CoachingDashboardService dashboard;

    public CoachingController(CoachingService coaching, CoachingExamService exams, CoachingDashboardService dashboard) {
        this.coaching = coaching;
        this.exams = exams;
        this.dashboard = dashboard;
    }

    /** All the user's centers together (centerId empty), or one center; coordinators and teachers get their own. */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:VIEW')")
    public Map<String, Object> dashboard(@RequestParam(required = false) Long centerId) {
        return dashboard.dashboard(centerId);
    }

    // ---------------------------------------------------------------- students

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:VIEW')")
    public Map<String, Object> students(@RequestParam(required = false) Long centerId, @RequestParam(required = false) Integer classLevel,
                                        @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return coaching.students(centerId, classLevel, status, q, page, size);
    }

    @PostMapping("/students")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:EDIT')")
    public Map<String, Object> addStudent(@RequestBody Map<String, Object> body) {
        return coaching.saveStudent(null, body);
    }

    @PutMapping("/students/{id}")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:EDIT')")
    public Map<String, Object> updateStudent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return coaching.saveStudent(id, body);
    }

    @DeleteMapping("/students/{id}")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:EDIT')")
    public Map<String, String> deleteStudent(@PathVariable Long id) {
        coaching.deleteStudent(id);
        return Map.of("message", "Student deleted.");
    }

    // ---------------------------------------------------------------- teachers

    @GetMapping("/teachers")
    @PreAuthorize("hasAuthority('COACHING_TEACHERS:VIEW')")
    public Map<String, Object> teachers(@RequestParam(required = false) Long centerId, @RequestParam(required = false) String q) {
        return coaching.teachers(centerId, q);
    }

    @PostMapping("/teachers")
    @PreAuthorize("hasAuthority('COACHING_TEACHERS:EDIT')")
    public Map<String, Object> addTeacher(@RequestBody Map<String, Object> body) {
        return coaching.saveTeacher(null, body);
    }

    @PutMapping("/teachers/{id}")
    @PreAuthorize("hasAuthority('COACHING_TEACHERS:EDIT')")
    public Map<String, Object> updateTeacher(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return coaching.saveTeacher(id, body);
    }

    @DeleteMapping("/teachers/{id}")
    @PreAuthorize("hasAuthority('COACHING_TEACHERS:EDIT')")
    public Map<String, String> deleteTeacher(@PathVariable Long id) {
        coaching.deleteTeacher(id);
        return Map.of("message", "Teacher deleted.");
    }

    // ---------------------------------------------------------------- subjects

    @GetMapping("/subjects")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:VIEW')")
    public List<Map<String, Object>> subjects() {
        return coaching.subjects();
    }

    /** The subject list is shared by every center: the office (not a center-scoped user) changes it. */
    @PostMapping("/subjects")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, Object> addSubject(@RequestBody Map<String, Object> body) {
        return coaching.saveSubject(null, body);
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, Object> updateSubject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return coaching.saveSubject(id, body);
    }

    @DeleteMapping("/subjects/{id}")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, String> deleteSubject(@PathVariable Long id) {
        coaching.deleteSubject(id);
        return Map.of("message", "Subject deleted.");
    }

    // ---------------------------------------------------------------- exams

    @GetMapping("/exams")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:VIEW')")
    public Map<String, Object> exams(@RequestParam(required = false) Long centerId, @RequestParam(required = false) Integer classLevel) {
        return exams.list(centerId, classLevel);
    }

    @PostMapping("/exams")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, Object> addExam(@RequestBody Map<String, Object> body) {
        return exams.save(null, body);
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, Object> updateExam(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return exams.save(id, body);
    }

    @DeleteMapping("/exams/{id}")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, String> deleteExam(@PathVariable Long id) {
        exams.delete(id);
        return Map.of("message", "Exam deleted.");
    }

    /** The marks sheet: subjects, the class's students, their marks, totals and prizes. */
    @GetMapping("/exams/{id}/marks")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:VIEW')")
    public Map<String, Object> marks(@PathVariable Long id) {
        return exams.sheet(id);
    }

    @PutMapping("/exams/{id}/marks")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, Object> saveMarks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return exams.saveMarks(id, body);
    }

    /** Works out the 1st / 2nd / 3rd prizes from the totals; returns the marks sheet. */
    @PostMapping("/exams/{id}/prizes")
    @PreAuthorize("hasAuthority('COACHING_EXAMS:EDIT')")
    public Map<String, Object> prizes(@PathVariable Long id) {
        return exams.generatePrizes(id);
    }
}
