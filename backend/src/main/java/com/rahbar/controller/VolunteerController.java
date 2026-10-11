package com.rahbar.controller;

import com.rahbar.service.VolunteerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Volunteer sign-ups: the public form (biharanjuman.com "Volunteer with us", POST /api/public/volunteer-applications)
 * and their follow-up in Admin > Volunteers.
 */
@RestController
public class VolunteerController {

    private final VolunteerService volunteerService;

    public VolunteerController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    /** Open to everyone (/api/public/**); see VolunteerService.submit for the fields. */
    @PostMapping("/api/public/volunteer-applications")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submit(@RequestBody Map<String, Object> body) {
        volunteerService.submit(body);
        return Map.of("message", "Thank you for offering to help! Our team will contact you soon.");
    }

    @GetMapping("/api/admin/volunteers")
    @PreAuthorize("hasAuthority('VOLUNTEERS:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String offer, @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        return volunteerService.list(q, status, offer, page, size);
    }

    /** Body: { status: NEW | CONTACTED | ONBOARDED | DECLINED, notes }. */
    @PutMapping("/api/admin/volunteers/{id}")
    @PreAuthorize("hasAuthority('VOLUNTEERS:EDIT')")
    public Map<String, String> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        volunteerService.update(id, body);
        return Map.of("message", "Saved.");
    }

    @DeleteMapping("/api/admin/volunteers/{id}")
    @PreAuthorize("hasAuthority('VOLUNTEERS:EDIT')")
    public Map<String, String> delete(@PathVariable Long id) {
        volunteerService.delete(id);
        return Map.of("message", "Volunteer sign-up deleted.");
    }
}
