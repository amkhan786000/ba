package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.OnlinePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Sponsor > Payments > Pay online (sponsors who pay through Rahbar). */
@RestController
@RequestMapping("/api/sponsor/online-payments")
@PreAuthorize("hasRole('5')")
public class SponsorOnlinePaymentController {

    private final OnlinePaymentService service;

    public SponsorOnlinePaymentController(OnlinePaymentService service) {
        this.service = service;
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }

    @GetMapping
    public Map<String, Object> info() {
        return service.sponsorInfo(me());
    }

    /** Body: { installmentIds: [...], pan, declaration: true }. Returns what the Razorpay payment window needs. */
    @PostMapping("/order")
    public Map<String, Object> order(@RequestBody Map<String, Object> body) {
        return service.sponsorOrder(me(), body);
    }

    /** Body: { razorpay_order_id, razorpay_payment_id, razorpay_signature } from the payment window. */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, String> body) {
        var p = service.confirm(body.get("razorpay_order_id"), body.get("razorpay_payment_id"), body.get("razorpay_signature"));
        return Map.of("message", "Thank you! Your payment was received. Your 80G receipt " + p.getReceiptNo() + " is on its way by email.",
                "receipt_no", p.getReceiptNo(), "id", p.getId());
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        return StatementResponses.pdf(service.receipt(id, me()), "Rahbar_80G_receipt_" + id + ".pdf");
    }
}
