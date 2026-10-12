package com.rahbar.controller;

import com.rahbar.exception.ApiException;
import com.rahbar.service.OnlineDonationService;
import com.rahbar.service.OnlinePaymentService;
import com.rahbar.util.WebsiteLimiter;
import com.rahbar.util.WebsiteSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** biharanjuman.com Donate page: Indian donations through Razorpay with an 80G receipt. */
@RestController
@RequestMapping("/api/public/website/donations")
public class WebsiteDonationController {

    private final OnlineDonationService donations;
    private final OnlinePaymentService online;
    /** Starting a payment: a few per visitor per hour, and a cap per day for the whole site. */
    private final WebsiteLimiter limit = new WebsiteLimiter(10, 500);

    public WebsiteDonationController(OnlineDonationService donations, OnlinePaymentService online) {
        this.donations = donations;
        this.online = online;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return donations.config();
    }

    /** Body: { name, email, phone, pan, address, amount, projectId, declaration, website (left empty: a trap for bots) }. */
    @PostMapping("/order")
    public Map<String, Object> order(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Object trap = body.get("website");
        if (trap != null && !String.valueOf(trap).isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please try again.");
        if (!limit.allow(WebsiteSupport.clientIp(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again in a while.");
        }
        return donations.order(body);
    }

    /** Body: { razorpay_order_id, razorpay_payment_id, razorpay_signature } from the payment window. */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, String> body) {
        var p = online.confirm(body.get("razorpay_order_id"), body.get("razorpay_payment_id"), body.get("razorpay_signature"));
        return Map.of("message", "Thank you for your donation! Your 80G receipt " + p.getReceiptNo() + " has been emailed to you.",
                "receipt_no", p.getReceiptNo());
    }
}
