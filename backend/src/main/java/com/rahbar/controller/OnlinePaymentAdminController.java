package com.rahbar.controller;

import com.rahbar.service.OnlinePaymentService;
import com.rahbar.service.OnlinePaymentSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Sponsors & Payments > Online Payments: payments and donations, passing money on to students, settings. */
@RestController
@RequestMapping("/api/admin/online-payments")
@PreAuthorize("denyAll()")
public class OnlinePaymentAdminController {

    private final OnlinePaymentService service;
    private final OnlinePaymentSettings settings;

    public OnlinePaymentAdminController(OnlinePaymentService service, OnlinePaymentSettings settings) {
        this.service = service;
        this.settings = settings;
    }

    /** purpose: SPONSOR or DONATION (none: both). */
    @GetMapping
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) String purpose) {
        return service.list(purpose);
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:VIEW')")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        return StatementResponses.pdf(service.receipt(id, null), "Rahbar_80G_receipt_" + id + ".pdf");
    }

    /** Body: { date, reference }. */
    @PostMapping("/items/{itemId}/pass-on")
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:EDIT')")
    public Map<String, String> passOn(@PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        service.passOn(itemId, body);
        return Map.of("message", "Saved. The student was told the money is on its way to their bank account.");
    }

    @GetMapping("/sponsors")
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:VIEW')")
    public Map<String, Object> sponsors() {
        return service.sponsors();
    }

    /** Body: { through: true | false }. */
    @PutMapping("/sponsors/{sponsorId}")
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:EDIT')")
    public Map<String, String> setSponsor(@PathVariable Long sponsorId, @RequestBody Map<String, Object> body) {
        boolean through = Boolean.parseBoolean(String.valueOf(body.get("through")));
        service.setThroughRahbar(sponsorId, through);
        return Map.of("message", through ? "This sponsor now pays through Rahbar online." : "This sponsor now pays the student directly.");
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:VIEW')")
    public Map<String, Object> settingsView() {
        return service.settingsView();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('ONLINE_PAYMENTS:EDIT')")
    public Map<String, String> saveSettings(@RequestBody Map<String, Object> body) {
        settings.save(body);
        return Map.of("message", "Saved.");
    }
}
