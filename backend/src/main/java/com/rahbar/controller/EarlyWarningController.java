package com.rahbar.controller;

import com.rahbar.service.EarlyWarningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Students > Early Warning: students at risk of dropping out, and follow-ups (limited to the user's students). */
@RestController
@RequestMapping("/api/admin/early-warning")
public class EarlyWarningController {

    private final EarlyWarningService service;

    public EarlyWarningController(EarlyWarningService service) {
        this.service = service;
    }

    /** view: NEEDS (default), FOLLOWED or ALL; kind: SCHOLAR, COACHING or empty; signal: one sign's code. */
    @GetMapping
    @PreAuthorize("hasAuthority('EARLY_WARNING:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) String kind, @RequestParam(required = false) String view,
                                    @RequestParam(required = false) String signal, @RequestParam(required = false) String q) {
        return service.list(kind, view, signal, q);
    }

    @GetMapping("/followups")
    @PreAuthorize("hasAuthority('EARLY_WARNING:VIEW')")
    public List<Map<String, Object>> history(@RequestParam String kind, @RequestParam Long studentId) {
        return service.history(kind, studentId);
    }

    /** Body: { kind, studentId, date, method, outcome, note, nextDate }. */
    @PostMapping("/followups")
    @PreAuthorize("hasAuthority('EARLY_WARNING:EDIT')")
    public Map<String, Object> record(@RequestBody Map<String, Object> body) {
        return service.record(body);
    }
}
