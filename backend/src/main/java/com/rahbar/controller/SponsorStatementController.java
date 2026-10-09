package com.rahbar.controller;

import com.rahbar.service.SponsorStatementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Office: any sponsor's yearly statement (download, or email it to the sponsor). */
@RestController
@RequestMapping("/api/admin/sponsors/{sponsorId}/statement")
@PreAuthorize("denyAll()")
public class SponsorStatementController {

    private final SponsorStatementService statementService;

    public SponsorStatementController(SponsorStatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public ResponseEntity<byte[]> download(@PathVariable Long sponsorId, @RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() - 1 : year;
        return StatementResponses.pdf(statementService.pdf(sponsorId, y),
                SponsorStatementService.fileName(statementService.requireSponsor(sponsorId), y));
    }

    @PostMapping("/email")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:EDIT')")
    public Map<String, Object> email(@PathVariable Long sponsorId, @RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() - 1 : year;
        boolean sent = statementService.email(sponsorId, y);
        return Map.of("sent", sent, "message", sent ? "The " + y + " statement was emailed to the sponsor."
                : "The sponsor has no usable email address, or the email could not be sent (see the Email Log).");
    }
}
