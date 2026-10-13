package com.rahbar.controller;

import com.rahbar.service.ApplicationService;
import com.rahbar.service.PublicService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public (no login) application tracking: look up an application by its number and a mobile on the form, and
 * upload its documents. The application form itself is on the website (WebsiteScholarshipController).
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicService publicService;
    private final ApplicationService applicationService;

    public PublicController(PublicService publicService, ApplicationService applicationService) {
        this.publicService = publicService;
        this.applicationService = applicationService;
    }

    /** "Track my application": needs the application number and one of the mobile numbers on the form. */
    @PostMapping("/track-application")
    public Map<String, Object> track(@RequestBody Map<String, String> body) {
        Long id;
        try {
            id = Long.valueOf(String.valueOf(body.get("applicationId")).trim());
        } catch (NumberFormatException e) {
            id = null;
        }
        return applicationService.track(id, body.get("mobile"));
    }

    @PostMapping(value = "/applications/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadDocument(@PathVariable Long id, @RequestParam String mobile,
                                              @RequestParam(required = false) String docType,
                                              @RequestParam MultipartFile file) {
        return applicationService.uploadByApplicant(id, mobile, docType, file);
    }

    // The application form itself is on the website: WebsiteScholarshipController (/api/public/website/scholarship).

    @GetMapping("/application-status/{applicationId}")
    public Map<String, Object> applicationStatus(@PathVariable Long applicationId) {
        return publicService.applicationStatus(applicationId);
    }

    @PostMapping("/check-application-status")
    public Map<String, Object> checkByMobile(@RequestBody Map<String, String> body) {
        return Map.of("applicationId", publicService.findApplicationIdByMobile(body.get("mobile")));
    }
}
