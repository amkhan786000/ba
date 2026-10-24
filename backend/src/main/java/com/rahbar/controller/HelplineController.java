package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.HelplineService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Communication > Helpline (office), and Helpline in the student and sponsor portals. */
@RestController
public class HelplineController {

    private final HelplineService service;

    public HelplineController(HelplineService service) {
        this.service = service;
    }

    /** status: OPEN, IN_PROGRESS, RESOLVED, CLOSED or ACTIVE (open + in progress); mine: only issues assigned to me. */
    @GetMapping("/api/admin/helpline")
    @PreAuthorize("hasAuthority('HELPLINE:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) String status, @RequestParam(required = false) String category,
                                    @RequestParam(required = false) Boolean mine, @RequestParam(required = false) String q) {
        return service.list(status, category, mine, q);
    }

    @GetMapping("/api/admin/helpline/{id}")
    @PreAuthorize("hasAuthority('HELPLINE:VIEW')")
    public Map<String, Object> detail(@PathVariable Long id) {
        return service.staffDetail(id);
    }

    /** Body: { message, internal, status, priority, assignedTo, confidential }. */
    @PutMapping("/api/admin/helpline/{id}")
    @PreAuthorize("hasAuthority('HELPLINE:EDIT')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.staffUpdate(id, body);
    }

    // ---------------------------------------------------------------- portals

    @GetMapping("/api/portal/helpline")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> mine() {
        return service.mine(AuthUtil.currentUser());
    }

    /** Body: { category, subject, message, confidential }. */
    @PostMapping("/api/portal/helpline")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> raise(@RequestBody Map<String, Object> body) {
        return service.raise(AuthUtil.currentUser(), body);
    }

    @GetMapping("/api/portal/helpline/{id}")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> mineDetail(@PathVariable Long id) {
        return service.mineDetail(AuthUtil.currentUser(), id);
    }

    /** Body: { message } or { solved: true }. */
    @PostMapping("/api/portal/helpline/{id}")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.replyMine(AuthUtil.currentUser(), id, body);
    }
}
