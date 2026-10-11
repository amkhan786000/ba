package com.rahbar.controller;

import com.rahbar.service.FinanceService;
import com.rahbar.util.Rows;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Administration > Income & Expenses, and the published yearly summary for biharanjuman.com. */
@RestController
public class FinanceController {

    private final FinanceService finance;

    public FinanceController(FinanceService finance) {
        this.finance = finance;
    }

    @GetMapping("/api/admin/finance/categories")
    @PreAuthorize("hasAuthority('FINANCE:VIEW')")
    public List<Map<String, Object>> categories() {
        return finance.categories();
    }

    @PostMapping("/api/admin/finance/categories")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, Object> addCategory(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Category added.", "data", Rows.of(finance.saveCategory(null, body)));
    }

    @PutMapping("/api/admin/finance/categories/{id}")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, Object> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Category saved.", "data", Rows.of(finance.saveCategory(id, body)));
    }

    @DeleteMapping("/api/admin/finance/categories/{id}")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, String> deleteCategory(@PathVariable Long id) {
        finance.deleteCategory(id);
        return Map.of("message", "Category deleted.");
    }

    @GetMapping("/api/admin/finance/projects")
    @PreAuthorize("hasAuthority('FINANCE:VIEW')")
    public List<Map<String, Object>> projects() {
        return finance.projectOptions();
    }

    @GetMapping("/api/admin/finance/entries")
    @PreAuthorize("hasAuthority('FINANCE:VIEW')")
    public Map<String, Object> entries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) String kind, @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) Long projectId, @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return finance.entries(from, to, kind, categoryId, projectId, q, page, size);
    }

    /** Body: { entryDate, categoryId, projectId, amount, party, description, method, reference }. */
    @PostMapping("/api/admin/finance/entries")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, Object> addEntry(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Entry saved.", "data", Rows.of(finance.saveEntry(null, body)));
    }

    @PutMapping("/api/admin/finance/entries/{id}")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, Object> updateEntry(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Entry saved.", "data", Rows.of(finance.saveEntry(id, body)));
    }

    @DeleteMapping("/api/admin/finance/entries/{id}")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, String> deleteEntry(@PathVariable Long id) {
        finance.deleteEntry(id);
        return Map.of("message", "Entry deleted.");
    }

    @GetMapping("/api/admin/finance/summary")
    @PreAuthorize("hasAuthority('FINANCE:VIEW')")
    public Map<String, Object> summary(@RequestParam int year) {
        return finance.summary(year);
    }

    /** Body: { year, note }. */
    @PostMapping("/api/admin/finance/published")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, String> publish(@RequestBody Map<String, Object> body) {
        finance.publish(body);
        return Map.of("message", "The summary is shown on the website.");
    }

    @DeleteMapping("/api/admin/finance/published/{year}")
    @PreAuthorize("hasAuthority('FINANCE:EDIT')")
    public Map<String, String> unpublish(@PathVariable int year) {
        finance.unpublish(year);
        return Map.of("message", "The summary is no longer shown on the website.");
    }

    // ---------------------------------------------------------------- public (biharanjuman.com)

    @GetMapping("/api/public/website/finance/years")
    public List<Integer> publicYears() {
        return finance.publicYears();
    }

    @GetMapping("/api/public/website/finance/{year}")
    public Map<String, Object> publicSummary(@PathVariable int year) {
        return finance.publicSummary(year);
    }
}
