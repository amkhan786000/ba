package com.rahbar.controller;

import com.rahbar.service.StudentNoteService;
import com.rahbar.util.Rows;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Students > Notes to sponsors: the office approves or returns notes students write to their sponsors. */
@RestController
@RequestMapping("/api/admin/student-notes")
@PreAuthorize("denyAll()")
public class StudentNoteController {

    private final StudentNoteService service;

    public StudentNoteController(StudentNoteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_NOTES:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    /** Body: { decision APPROVE | RETURN, note }. */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('STUDENT_NOTES:EDIT')")
    public Map<String, Object> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        var n = service.review(id, body);
        return Map.of("message", "APPROVED".equals(n.getStatus()) ? "Approved: the sponsor can read it now." : "Returned to the student.",
                "data", Rows.of(n));
    }
}
