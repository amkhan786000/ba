package com.rahbar.controller;

import com.rahbar.service.FaqService;
import com.rahbar.util.Rows;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Help & FAQ in Rahbar (everyone signed in reads; FAQS:EDIT manages) and the biharanjuman.com FAQ page. */
@RestController
public class FaqController {

    private final FaqService service;

    public FaqController(FaqService service) {
        this.service = service;
    }

    /** asRole: the role of the portal the page is shown in (sponsor, student, ...): that role's questions. */
    @GetMapping("/api/faqs")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> list(@RequestParam(required = false) Integer asRole) {
        return service.list(asRole);
    }

    @PostMapping("/api/admin/faqs")
    @PreAuthorize("hasAuthority('FAQS:EDIT')")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        return Map.of("message", "FAQ added.", "data", Rows.of(service.save(null, body)));
    }

    @PutMapping("/api/admin/faqs/{id}")
    @PreAuthorize("hasAuthority('FAQS:EDIT')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "FAQ saved.", "data", Rows.of(service.save(id, body)));
    }

    @DeleteMapping("/api/admin/faqs/{id}")
    @PreAuthorize("hasAuthority('FAQS:EDIT')")
    public Map<String, String> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "FAQ deleted.");
    }

    /** biharanjuman.com: only the FAQs switched on for the website. */
    @GetMapping("/api/public/website/faqs")
    public List<Map<String, Object>> website() {
        return service.website();
    }
}
