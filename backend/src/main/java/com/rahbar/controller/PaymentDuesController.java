package com.rahbar.controller;

import com.rahbar.service.PaymentReminderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin "Payment Dues": installments due vs paid per sponsored student, and sending reminders on demand. */
@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class PaymentDuesController {

    private final PaymentReminderService paymentReminderService;

    public PaymentDuesController(PaymentReminderService paymentReminderService) {
        this.paymentReminderService = paymentReminderService;
    }

    @GetMapping("/dues")
    @PreAuthorize("hasAuthority('PAYMENT_DUES:VIEW')")
    public List<Map<String, Object>> dues() {
        return paymentReminderService.dues();
    }

    /** Sends any reminders that haven't gone out yet (the same job runs automatically every morning). */
    @PostMapping("/reminders/run")
    @PreAuthorize("hasAuthority('PAYMENT_DUES:EDIT')")
    public Map<String, Object> runReminders() {
        Map<String, Integer> sent = paymentReminderService.sendReminders();
        return Map.of("message", sent.get("overdueReminders") + " overdue and " + sent.get("upcomingReminders")
                + " upcoming reminder(s) sent.", "sent", sent);
    }
}
