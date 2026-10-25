package com.rahbar.controller;

import com.rahbar.service.StudentReferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Students > Reference Numbers: only the Super Admin (role 1) and the Application Admin (role 2). */
@RestController
@RequestMapping("/api/admin/student-references")
@PreAuthorize("hasAnyRole('1', '2')")
public class StudentReferenceController {

    private final StudentReferenceService service;

    public StudentReferenceController(StudentReferenceService service) {
        this.service = service;
    }

    /** year (default: the current one): the years, chapters with how many are left, the next numbers and batches. */
    @GetMapping
    public Map<String, Object> overview(@RequestParam(required = false) Integer year) {
        return service.overview(year);
    }

    /**
     * Body: { year, chapterId } (chapterId: only that chapter's students; 0: those without a chapter; left out:
     * every chapter): numbers the students of that session year who have none, carrying on from the last number.
     */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        Object year = body == null ? null : body.get("year");
        Object chapter = body == null ? null : body.get("chapterId");
        return service.generate(year == null || String.valueOf(year).isBlank() ? null : Integer.valueOf(String.valueOf(year).trim()),
                chapter == null || String.valueOf(chapter).isBlank() ? null : Long.valueOf(String.valueOf(chapter).trim()));
    }
}
