package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.ImpactReportService;
import com.rahbar.service.SponsorRenewalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Sponsors & Payments > Sponsor Renewal (office), and the sponsor portal's Renewal and My Impact pages. */
@RestController
public class SponsorRenewalController {

    private final SponsorRenewalService renewals;
    private final ImpactReportService impact;

    public SponsorRenewalController(SponsorRenewalService renewals, ImpactReportService impact) {
        this.renewals = renewals;
        this.impact = impact;
    }

    // ---------------------------------------------------------------- office

    @GetMapping("/api/admin/renewals")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public Map<String, Object> campaigns() {
        return renewals.campaigns();
    }

    @GetMapping("/api/admin/renewals/{id}")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return renewals.detail(id);
    }

    /** Body: { sessionYear, deadline, message }: asks every active sponsor with students, by email. */
    @PostMapping("/api/admin/renewals")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> start(@RequestBody Map<String, Object> body) {
        return renewals.start(body);
    }

    @PostMapping("/api/admin/renewals/{id}/remind")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> remind(@PathVariable Long id) {
        return renewals.remind(id);
    }

    /** Body: { sponsorId, answer: CONTINUE | CHANGE | STOP, studentsWanted, note }: an answer given on the phone. */
    @PutMapping("/api/admin/renewals/{id}/answer")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> record(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return renewals.record(id, body);
    }

    @PostMapping("/api/admin/renewals/{id}/close")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> close(@PathVariable Long id) {
        return renewals.close(id);
    }

    // ---------------------------------------------------------------- sponsor portal

    @GetMapping("/api/sponsor/renewal")
    @PreAuthorize("hasRole('5')")
    public Map<String, Object> mine() {
        return renewals.mine();
    }

    @PutMapping("/api/sponsor/renewal")
    @PreAuthorize("hasRole('5')")
    public Map<String, Object> answer(@RequestBody Map<String, Object> body) {
        return renewals.answerMine(body);
    }

    @GetMapping("/api/sponsor/impact")
    @PreAuthorize("hasRole('5')")
    public Map<String, Object> myImpact() {
        return impact.sponsor(AuthUtil.currentUser().getId());
    }

    @GetMapping("/api/sponsor/impact/pdf")
    @PreAuthorize("hasRole('5')")
    public ResponseEntity<byte[]> myImpactPdf() {
        return StatementResponses.pdf(impact.sponsorPdf(AuthUtil.currentUser().getId()), "My-Rahbar-impact.pdf");
    }
}
