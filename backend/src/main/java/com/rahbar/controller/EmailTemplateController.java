package com.rahbar.controller;

import com.rahbar.service.EmailTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Email Templates: the wording of every kind of email the application sends. */
@RestController
@RequestMapping("/api/admin/email-templates")
@PreAuthorize("denyAll()")
public class EmailTemplateController {

    private final EmailTemplateService templateService;

    public EmailTemplateController(EmailTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATES:VIEW')")
    public List<Map<String, Object>> list() {
        return templateService.list();
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATES:VIEW')")
    public Map<String, Object> get(@PathVariable String key) {
        return templateService.get(key);
    }

    /** Body: { "subject": "...", "body": "..." }. */
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATES:EDIT')")
    public Map<String, Object> save(@PathVariable String key, @RequestBody Map<String, String> body) {
        return templateService.save(key, body.get("subject"), body.get("body"));
    }

    /** Back to the built-in wording (Super Admin only; checked in the service). */
    @DeleteMapping("/{key}")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATES:EDIT')")
    public Map<String, String> delete(@PathVariable String key) {
        templateService.delete(key);
        return Map.of("message", "The custom template was deleted; this email uses the built-in wording again.");
    }

    /** The subject and text filled in with sample values. */
    @PostMapping("/{key}/preview")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATES:VIEW')")
    public Map<String, Object> preview(@PathVariable String key, @RequestBody Map<String, String> body) {
        return templateService.preview(key, body.get("subject"), body.get("body"));
    }
}
