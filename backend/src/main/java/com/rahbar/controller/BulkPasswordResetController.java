package com.rahbar.controller;

import com.rahbar.service.BulkPasswordResetService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administration > Bulk Password Reset (the service also checks for the Super Admin). */
@RestController
@RequestMapping("/api/admin/bulk-password-reset")
@PreAuthorize("hasAuthority('USERS:EDIT')")
public class BulkPasswordResetController {

    private final BulkPasswordResetService service;

    public BulkPasswordResetController(BulkPasswordResetService service) {
        this.service = service;
    }

    /** Roles and students' session years, with how many accounts each would reset. */
    @GetMapping
    public Map<String, Object> options() {
        return service.options();
    }

    /** Body: { roleId, years }: who would be reset and who skipped. */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> body) {
        return service.preview(body);
    }

    /** Body: { roleId, years }: starts the reset in the background. */
    @PostMapping
    public Map<String, Object> start(@RequestBody Map<String, Object> body) {
        return service.start(body);
    }

    /** The current or last run. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return service.status();
    }

    /** Who has chosen their own password, and who still has a temporary one. */
    @GetMapping("/temporary-passwords")
    public Map<String, Object> temporaryPasswords() {
        return service.temporaryPasswords();
    }
}
