package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.SponsorFilesService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Sponsor > Payments downloads: a receipt per payment, the payment history (Excel) and calendar reminders. */
@RestController
@RequestMapping("/api/sponsor/payments")
@PreAuthorize("hasRole('5')")
public class SponsorFilesController {

    private final SponsorFilesService files;
    private final com.rahbar.service.OnlinePaymentService online;

    public SponsorFilesController(SponsorFilesService files, com.rahbar.service.OnlinePaymentService online) {
        this.files = files;
        this.online = online;
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }

    @GetMapping("/{paymentId}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long paymentId) {
        // Paid online through Rahbar: the 80G receipt of that online payment instead.
        Long onlineId = online.onlinePaymentOf(paymentId);
        if (onlineId != null) return StatementResponses.pdf(online.receipt(onlineId, me()), "Rahbar_80G_receipt_" + onlineId + ".pdf");
        return StatementResponses.pdf(files.receipt(me(), paymentId), "Rahbar_receipt_" + paymentId + ".pdf");
    }

    /** year: one calendar year; none: every payment. */
    @GetMapping("/history")
    public ResponseEntity<ByteArrayResource> history(@RequestParam(required = false) Integer year) {
        return Downloads.of(files.history(me(), year));
    }

    @GetMapping("/calendar")
    public ResponseEntity<byte[]> calendar() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Rahbar_payments.ics").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(files.calendar(me()));
    }
}
