package com.rahbar.controller;

import com.rahbar.service.OnlinePaymentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Razorpay's webhook (Razorpay dashboard > Webhooks: this address on rahbar.biharanjuman.com, events payment.captured
 * and order.paid, with the RAZORPAY_WEBHOOK_SECRET). Records a payment even when the payer's browser closed early.
 */
@RestController
public class RazorpayWebhookController {

    private final OnlinePaymentService service;

    public RazorpayWebhookController(OnlinePaymentService service) {
        this.service = service;
    }

    @PostMapping("/api/public/payments/razorpay/webhook")
    public Map<String, String> webhook(@RequestBody String body, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        service.webhook(body, signature);
        return Map.of("status", "ok");
    }
}
