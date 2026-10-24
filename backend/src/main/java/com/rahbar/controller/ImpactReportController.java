package com.rahbar.controller;

import com.rahbar.security.StudentScope;
import com.rahbar.service.ImpactReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Reports > Impact Report (a chapter lead: their chapter's), and a sponsor's impact for the office. Counts only. */
@RestController
@RequestMapping("/api/admin")
public class ImpactReportController {

    private final ImpactReportService service;
    private final StudentScope studentScope;

    public ImpactReportController(ImpactReportService service, StudentScope studentScope) {
        this.service = service;
        this.studentScope = studentScope;
    }

    /** year: a session year; empty: all years. */
    @GetMapping("/impact")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public Map<String, Object> organisation(@RequestParam(required = false) Integer year) {
        return service.organisation(year);
    }

    @GetMapping("/impact/pdf")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public ResponseEntity<byte[]> organisationPdf(@RequestParam(required = false) Integer year) {
        return StatementResponses.pdf(service.organisationPdf(year), "Rahbar-impact-" + (year == null ? "all-years" : year) + ".pdf");
    }

    /** A sponsor's own impact report, as they see it in their portal. */
    @GetMapping("/sponsors/{sponsorId}/impact/pdf")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public ResponseEntity<byte[]> sponsorPdf(@PathVariable Long sponsorId) {
        studentScope.requireSponsor(sponsorId);
        return StatementResponses.pdf(service.sponsorPdf(sponsorId), "Rahbar-impact-sponsor-" + sponsorId + ".pdf");
    }
}
