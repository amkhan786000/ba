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

    /** The current year, how many students still have no reference number, and the numbers given so far. */
    @GetMapping
    public Map<String, Object> overview() {
        return service.overview();
    }

    /** Body: { year } (the current year): numbers every student of that session year who has none. */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        Object year = body == null ? null : body.get("year");
        return service.generate(year == null || String.valueOf(year).isBlank() ? null : Integer.valueOf(String.valueOf(year).trim()));
    }
}
