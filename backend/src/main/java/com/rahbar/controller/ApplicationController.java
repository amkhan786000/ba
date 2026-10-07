package com.rahbar.controller;

import com.rahbar.service.ApplicationService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Staff view of one application: details + history + documents, interview scheduling, document upload. */
@RestController
@RequestMapping("/api/admin/applications/{id}")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/details")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW') or hasAnyRole('3','4')")
    public Map<String, Object> details(@PathVariable Long id) {
        return applicationService.details(id);
    }

    /** Body: { interviewAt: "2026-10-12T10:30", venue } */
    @PostMapping("/interview")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    public Map<String, String> scheduleInterview(@PathVariable Long id, @RequestBody Map<String, String> body) {
        applicationService.scheduleInterview(id, body.get("interviewAt"), body.get("venue"));
        return Map.of("message", "Interview scheduled.");
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    public Map<String, Object> uploadDocument(@PathVariable Long id, @RequestParam(required = false) String docType,
                                              @RequestParam MultipartFile file) {
        return applicationService.uploadByStaff(id, docType, file);
    }
}
