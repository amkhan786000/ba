package com.rahbar.controller;

import com.rahbar.service.MentoringService;
import com.rahbar.util.Rows;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Students > Mentoring: volunteers and graduates paired with scholarship students, and suggested matches. */
@RestController
@RequestMapping("/api/admin/mentoring")
@PreAuthorize("denyAll()")
public class MentoringController {

    private final MentoringService service;

    public MentoringController(MentoringService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MENTORING:VIEW')")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('MENTORING:VIEW')")
    public List<Map<String, Object>> students() {
        return service.studentList();
    }

    @GetMapping("/mentors")
    @PreAuthorize("hasAuthority('MENTORING:VIEW')")
    public List<Map<String, Object>> mentors() {
        return service.mentorList();
    }

    @GetMapping("/students/{studentId}/suggestions")
    @PreAuthorize("hasAuthority('MENTORING:VIEW')")
    public Map<String, Object> suggestions(@PathVariable Long studentId) {
        return service.suggestions(studentId);
    }

    /** Body: { studentId, volunteerId | alumniUserId, help MENTOR | TUTOR, focus, startedOn, notes }. */
    @PostMapping
    @PreAuthorize("hasAuthority('MENTORING:EDIT')")
    public Map<String, Object> pair(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Paired.", "data", Rows.of(service.pair(body)));
    }

    /** Body: any of { status ACTIVE | ENDED, endedOn, focus, notes }. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENTORING:EDIT')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Saved.", "data", Rows.of(service.update(id, body)));
    }
}
