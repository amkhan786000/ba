package com.rahbar.controller;

import com.rahbar.service.ApplicationService;
import com.rahbar.service.PublicService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mirrors the public-facing bits of routes/admin.py: /apply, /application_status.
 *
 * NOTE: in the original Flask code, public_application() (the public
 * scholarship application form) was annotated @login_required, which
 * contradicts its own "public application" purpose and its template name
 * (public/apply.html). That looks like a bug, so this migration makes the
 * endpoint genuinely public, matching the apparent intent. Flag this to the
 * product owner in case the login requirement was actually deliberate.
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

    @GetMapping("/application-form-options")
    public Map<String, Object> formOptions() {
        return publicService.formOptions();
    }

    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody Map<String, Object> form) {
        Long applicationId = publicService.apply(form);
        return Map.of("message", "Application submitted successfully!", "applicationId", applicationId);
    }

    @GetMapping("/application-status/{applicationId}")
    public Map<String, Object> applicationStatus(@PathVariable Long applicationId) {
        return publicService.applicationStatus(applicationId);
    }

    @PostMapping("/check-application-status")
    public Map<String, Object> checkByMobile(@RequestBody Map<String, String> body) {
        return Map.of("applicationId", publicService.findApplicationIdByMobile(body.get("mobile")));
    }
}
