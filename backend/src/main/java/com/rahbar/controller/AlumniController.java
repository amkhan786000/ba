package com.rahbar.controller;

import com.rahbar.service.AlumniService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin > Alumni: graduated students and what they do now. */
@RestController
@RequestMapping("/api/admin/alumni")
@PreAuthorize("denyAll()")
public class AlumniController {

    private final AlumniService alumniService;

    public AlumniController(AlumniService alumniService) {
        this.alumniService = alumniService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALUMNI:VIEW')")
    public Map<String, Object> list() {
        return alumniService.list();
    }

    /** Body: { currentStatus, organisation, roleTitle, city, country, linkedinUrl, graduationYear, consentToContact, notes }. */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('ALUMNI:EDIT')")
    public Map<String, Object> save(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        return alumniService.save(userId, body);
    }
}
