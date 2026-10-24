package com.rahbar.controller;

import com.rahbar.service.SponsorApplicationService;
import com.rahbar.service.SponsorPreferenceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Applications > Apply for Sponsorship (sponsors and, on their behalf, the office), and the sponsor preference choices. */
@RestController
@RequestMapping("/api/sponsor-applications")
public class SponsorApplicationController {

    private final SponsorApplicationService service;
    private final SponsorPreferenceService preferences;

    public SponsorApplicationController(SponsorApplicationService service, SponsorPreferenceService preferences) {
        this.service = service;
        this.preferences = preferences;
    }

    /** Is a session open; its dates, the choices and who the user may apply for. */
    @GetMapping("/form")
    @PreAuthorize("hasAuthority('SPONSOR_APPLY:VIEW')")
    public Map<String, Object> form() {
        return service.form();
    }

    /** A sponsor's application this session (or their preferences), to start the form with. */
    @GetMapping("/for/{sponsorId}")
    @PreAuthorize("hasAuthority('SPONSOR_APPLY:VIEW')")
    public Map<String, Object> current(@PathVariable Long sponsorId) {
        return service.current(sponsorId);
    }

    /** Applies (or updates the application) for the active session. */
    @PostMapping
    @PreAuthorize("hasAuthority('SPONSOR_APPLY:VIEW')")
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        return service.apply(body);
    }

    /** Everyone's applications for a period (default: the active one). */
    @GetMapping
    @PreAuthorize("hasAuthority('SPONSOR_APPLY:EDIT')")
    public Map<String, Object> list(@RequestParam(required = false) Long periodId) {
        return service.list(periodId);
    }

    /** Students without a sponsor who fit an application's preferences. */
    @GetMapping("/{applicationId}/suggestions")
    @PreAuthorize("hasAuthority('SPONSOR_APPLY:EDIT')")
    public Map<String, Object> suggestions(@PathVariable Long applicationId) {
        return service.suggestions(applicationId);
    }

    /** Fathers' professions and the income range the students have (Edit Sponsor and the application form). */
    @GetMapping("/preference-options")
    @PreAuthorize("hasAnyAuthority('SPONSOR_APPLY:VIEW', 'SPONSOR_DETAILS:EDIT')")
    public Map<String, Object> preferenceOptions() {
        return preferences.options();
    }
}
