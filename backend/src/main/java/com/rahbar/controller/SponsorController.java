package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.SponsorService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Mirrors routes/sponsor.py (role_id 5). */
@RestController
@RequestMapping("/api/sponsor")
@PreAuthorize("hasRole('5')")
public class SponsorController {

    private final SponsorService sponsorService;

    public SponsorController(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return sponsorService.dashboard(me());
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(@RequestParam(required = false) Long granteeId) {
        return sponsorService.payments(me(), granteeId);
    }

    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> recordPayment(@RequestParam Long granteeId,
                                             @RequestParam BigDecimal amount,
                                             @RequestParam String paymentDate,
                                             @RequestParam MultipartFile receipt,
                                             @RequestParam(required = false) Long installmentId) {
        sponsorService.recordPayment(me(), granteeId, amount, paymentDate, receipt, installmentId);
        return Map.of("message", "Payment recorded successfully!");
    }

    @GetMapping("/student-progress")
    public List<Map<String, Object>> studentProgress() {
        return sponsorService.studentProgress(me());
    }
}
