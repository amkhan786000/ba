package com.rahbar.controller;

import com.rahbar.service.EmailLogService;
import com.rahbar.service.PaymentInstallmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin > Payment Records (sponsor-student installments) and Admin > Email Log. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("denyAll()")
public class PaymentRecordsController {

    private final PaymentInstallmentService installmentService;
    private final EmailLogService emailLogService;

    public PaymentRecordsController(PaymentInstallmentService installmentService, EmailLogService emailLogService) {
        this.installmentService = installmentService;
        this.emailLogService = emailLogService;
    }

    /** Installments of every student the user may see; status is Paid, Due or Not Due. */
    @GetMapping("/payment-records")
    @PreAuthorize("hasAuthority('PAYMENT_RECORDS:VIEW')")
    public List<Map<String, Object>> paymentRecords(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String status) {
        return installmentService.search(q, status);
    }

    /** One student's installments (all sponsors), and why there are none when there are none. */
    @GetMapping("/students/{userId}/installments")
    @PreAuthorize("hasAuthority('PAYMENT_RECORDS:VIEW')")
    public Map<String, Object> studentInstallments(@PathVariable Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("installments", installmentService.forStudent(userId));
        body.put("problem", installmentService.problem(userId));
        return body;
    }

    /** Fee schedule of each student mapped to the sponsor (installments, or what is missing to build them). */
    @GetMapping("/sponsors/{sponsorId}/fee-schedule")
    @PreAuthorize("hasAuthority('PAYMENT_RECORDS:VIEW')")
    public List<Map<String, Object>> sponsorFeeSchedule(@PathVariable Long sponsorId) {
        return installmentService.sponsorOverview(sponsorId);
    }

    /** Generates / updates the fee schedules of the sponsor's students (paid installments are never changed). */
    @PostMapping("/sponsors/{sponsorId}/fee-schedule")
    @PreAuthorize("hasAuthority('PAYMENT_RECORDS:EDIT')")
    public Map<String, Object> generateFeeSchedule(@PathVariable Long sponsorId) {
        return installmentService.generateForSponsor(sponsorId);
    }

    @GetMapping("/email-log")
    @PreAuthorize("hasAuthority('EMAIL_LOG:VIEW')")
    public Map<String, Object> emailLog(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long recipientId,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size) {
        return emailLogService.search(q, status, recipientId, from, to, page, size);
    }

    @GetMapping("/email-log/{id}")
    @PreAuthorize("hasAuthority('EMAIL_LOG:VIEW')")
    public Map<String, Object> email(@PathVariable Long id) {
        return emailLogService.get(id);
    }
}
