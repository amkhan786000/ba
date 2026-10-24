package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.VolunteerHoursService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Community > Volunteer Hours (staff), and "My volunteer hours" in the student and sponsor portals. */
@RestController
public class VolunteerHoursController {

    private final VolunteerHoursService service;

    public VolunteerHoursController(VolunteerHoursService service) {
        this.service = service;
    }

    /** status: APPROVED, PENDING, REJECTED or empty; period default: this year. */
    @GetMapping("/api/admin/volunteer-hours")
    @PreAuthorize("hasAuthority('VOLUNTEER_HOURS:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) String q) {
        return service.list(status, from, to, q);
    }

    /** Body: { personKind: VOLUNTEER | USER, personId, activity, workedOn, hours, description }. */
    @PostMapping("/api/admin/volunteer-hours")
    @PreAuthorize("hasAuthority('VOLUNTEER_HOURS:EDIT')")
    public Map<String, Object> log(@RequestBody Map<String, Object> body) {
        return service.log(body);
    }

    /** Body: { approve: true | false }. */
    @PutMapping("/api/admin/volunteer-hours/{id}/review")
    @PreAuthorize("hasAuthority('VOLUNTEER_HOURS:EDIT')")
    public Map<String, Object> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.review(id, Boolean.TRUE.equals(body.get("approve")));
    }

    @DeleteMapping("/api/admin/volunteer-hours/{id}")
    @PreAuthorize("hasAuthority('VOLUNTEER_HOURS:EDIT')")
    public Map<String, String> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Hours deleted.");
    }

    @GetMapping("/api/admin/volunteer-hours/people")
    @PreAuthorize("hasAuthority('VOLUNTEER_HOURS:EDIT')")
    public List<Map<String, Object>> people(@RequestParam(required = false) String q) {
        return service.people(q);
    }

    /** kind: VOLUNTEER or USER. */
    @GetMapping("/api/admin/volunteer-hours/certificate")
    @PreAuthorize("hasAuthority('VOLUNTEER_HOURS:VIEW')")
    public ResponseEntity<byte[]> certificate(@RequestParam String kind, @RequestParam Long id,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return StatementResponses.pdf(service.certificate(kind, id, from, to), "Volunteer-certificate.pdf");
    }

    // ---------------------------------------------------------------- portals

    @GetMapping("/api/portal/volunteer-hours")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> mine() {
        return service.mine(AuthUtil.currentUser().getId());
    }

    @PostMapping("/api/portal/volunteer-hours")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> logMine(@RequestBody Map<String, Object> body) {
        return service.logMine(AuthUtil.currentUser(), body);
    }

    @GetMapping("/api/portal/volunteer-hours/certificate")
    @PreAuthorize("hasAnyRole('5', '6')")
    public ResponseEntity<byte[]> myCertificate() {
        return StatementResponses.pdf(service.certificate(VolunteerHoursService.USER, AuthUtil.currentUser().getId(), null, null), "My-volunteer-certificate.pdf");
    }
}
