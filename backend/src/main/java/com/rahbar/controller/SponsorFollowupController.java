package com.rahbar.controller;

import com.rahbar.service.SponsorFollowupService;
import com.rahbar.util.Rows;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Sponsors & Payments > Sponsor Follow-up: lapsed and available sponsors, calls, notes and follow-up emails. */
@RestController
@RequestMapping("/api/admin/sponsor-followup")
@PreAuthorize("denyAll()")
public class SponsorFollowupController {

    private final SponsorFollowupService service;

    public SponsorFollowupController(SponsorFollowupService service) {
        this.service = service;
    }

    @GetMapping("/lapsed")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public List<Map<String, Object>> lapsed(@RequestParam(required = false) Integer days) {
        return service.lapsed(days);
    }

    @GetMapping("/available")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public List<Map<String, Object>> available() {
        return service.available();
    }

    @GetMapping("/{sponsorId}/history")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public List<Map<String, Object>> history(@PathVariable Long sponsorId) {
        return service.historyOf(sponsorId);
    }

    /** Body: { sponsorId, kind, channel CALL | NOTE, note }. */
    @PostMapping("/notes")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> record(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Saved.", "data", Rows.of(service.record(body)));
    }

    /** Body: { sponsorIds, kind LAPSED | AVAILABLE, message, force }. */
    @PostMapping("/emails")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> email(@RequestBody Map<String, Object> body) {
        return service.email(body);
    }
}
