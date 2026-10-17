package com.rahbar.controller;

import com.rahbar.service.PaymentReminderService;
import com.rahbar.util.Paging;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin "Payment Dues": installments due vs paid per sponsored student, and sending reminders on demand. */
@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class PaymentDuesController {

    private final PaymentReminderService paymentReminderService;

    private final com.rahbar.security.StudentScope studentScope;

    public PaymentDuesController(PaymentReminderService paymentReminderService, com.rahbar.security.StudentScope studentScope) {
        this.studentScope = studentScope;
        this.paymentReminderService = paymentReminderService;
    }

    /** One page of dues (one row per sponsored student), filtered by status and text, with the count of each status. */
    @GetMapping("/dues")
    @PreAuthorize("hasAuthority('PAYMENT_DUES:VIEW')")
    public Map<String, Object> dues(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size) {
        // Chapter / RCC-scoped users: only their own students' dues.
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        Map<Long, com.rahbar.entity.User> users = scope.all() ? Map.of() : studentScope.usersById();
        List<Map<String, Object>> all = paymentReminderService.dues().stream()
                .filter(d -> scope.includesId(((Number) d.get("student_id")).longValue(), users)).toList();
        List<Integer> years = all.stream().map(d -> d.get("session_year")).filter(Integer.class::isInstance).map(Integer.class::cast)
                .distinct().sorted(java.util.Comparator.reverseOrder()).toList();
        all = all.stream().filter(d -> year == null || year.equals(d.get("session_year"))).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : List.of("Overdue", "Due soon", "On schedule", "Completed", "On hold")) counts.put(s, 0L);
        all.forEach(d -> counts.merge(String.valueOf(d.get("status")), 1L, Long::sum));
        List<Map<String, Object>> filtered = all.stream()
                .filter(d -> status == null || status.isBlank() || status.equals(d.get("status")))
                .filter(d -> Paging.matches(q, d.get("student_name"), d.get("student_code"), d.get("sponsor_name"), d.get("sponsor_code")))
                .toList();
        Map<String, Object> body = Paging.of(filtered, page, size);
        body.put("summary", counts);
        body.put("years", years);
        return body;
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
