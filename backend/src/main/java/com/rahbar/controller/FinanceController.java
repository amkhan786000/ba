package com.rahbar.controller;

import com.rahbar.service.FinanceService;
import com.rahbar.util.Rows;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The two books of income & expenses: /api/admin/books/rahbar/... (Administration > Rahbar Income & Expenses,
 * permission FINANCE) and /api/admin/books/reb/... (Rahbar-e-Banaat > REB Income & Expenses, permission REB_FINANCE),
 * and their published yearly summaries for biharanjuman.com.
 */
@RestController
public class FinanceController {

    private static final String VIEW = "@books.may(#book, 'VIEW')";
    private static final String EDIT = "@books.may(#book, 'EDIT')";

    private final FinanceService finance;

    public FinanceController(FinanceService finance) {
        this.finance = finance;
    }

    @GetMapping("/api/admin/books/{book}/categories")
    @PreAuthorize(VIEW)
    public List<Map<String, Object>> categories(@PathVariable String book) {
        return finance.categories(FinanceService.book(book));
    }

    @PostMapping("/api/admin/books/{book}/categories")
    @PreAuthorize(EDIT)
    public Map<String, Object> addCategory(@PathVariable String book, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Category added.", "data", Rows.of(finance.saveCategory(FinanceService.book(book), null, body)));
    }

    @PutMapping("/api/admin/books/{book}/categories/{id}")
    @PreAuthorize(EDIT)
    public Map<String, Object> updateCategory(@PathVariable String book, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Category saved.", "data", Rows.of(finance.saveCategory(FinanceService.book(book), id, body)));
    }

    @DeleteMapping("/api/admin/books/{book}/categories/{id}")
    @PreAuthorize(EDIT)
    public Map<String, String> deleteCategory(@PathVariable String book, @PathVariable Long id) {
        finance.deleteCategory(FinanceService.book(book), id);
        return Map.of("message", "Category deleted.");
    }

    @GetMapping("/api/admin/books/{book}/projects")
    @PreAuthorize(VIEW)
    public List<Map<String, Object>> projects(@PathVariable String book) {
        return finance.projectOptions();
    }

    @GetMapping("/api/admin/books/{book}/entries")
    @PreAuthorize(VIEW)
    public Map<String, Object> entries(@PathVariable String book,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) String kind, @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) Long projectId, @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return finance.entries(FinanceService.book(book), from, to, kind, categoryId, projectId, q, page, size);
    }

    /** Body: { entryDate, categoryId, projectId, amount, party, description, method, reference }. */
    @PostMapping("/api/admin/books/{book}/entries")
    @PreAuthorize(EDIT)
    public Map<String, Object> addEntry(@PathVariable String book, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Entry saved.", "data", Rows.of(finance.saveEntry(FinanceService.book(book), null, body)));
    }

    @PutMapping("/api/admin/books/{book}/entries/{id}")
    @PreAuthorize(EDIT)
    public Map<String, Object> updateEntry(@PathVariable String book, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Entry saved.", "data", Rows.of(finance.saveEntry(FinanceService.book(book), id, body)));
    }

    @DeleteMapping("/api/admin/books/{book}/entries/{id}")
    @PreAuthorize(EDIT)
    public Map<String, String> deleteEntry(@PathVariable String book, @PathVariable Long id) {
        finance.deleteEntry(FinanceService.book(book), id);
        return Map.of("message", "Entry deleted.");
    }

    /** Received, spent and balance (all time, this year, this month) and the last 12 months. */
    @GetMapping("/api/admin/books/{book}/balance")
    @PreAuthorize(VIEW)
    public Map<String, Object> balance(@PathVariable String book) {
        return finance.balance(FinanceService.book(book));
    }

    @GetMapping("/api/admin/books/{book}/summary")
    @PreAuthorize(VIEW)
    public Map<String, Object> summary(@PathVariable String book, @RequestParam int year) {
        return finance.summary(FinanceService.book(book), year);
    }

    /** Body: { year, note }. */
    @PostMapping("/api/admin/books/{book}/published")
    @PreAuthorize(EDIT)
    public Map<String, String> publish(@PathVariable String book, @RequestBody Map<String, Object> body) {
        finance.publish(FinanceService.book(book), body);
        return Map.of("message", "The summary is shown on the website.");
    }

    @DeleteMapping("/api/admin/books/{book}/published/{year}")
    @PreAuthorize(EDIT)
    public Map<String, String> unpublish(@PathVariable String book, @PathVariable int year) {
        finance.unpublish(FinanceService.book(book), year);
        return Map.of("message", "The summary is no longer shown on the website.");
    }

    // ---------------------------------------------------------------- public (biharanjuman.com)

    /** Published years of a book (?book=rahbar, the default, or reb). */
    @GetMapping("/api/public/website/finance/years")
    public List<Integer> publicYears(@RequestParam(defaultValue = "rahbar") String book) {
        return finance.publicYears(FinanceService.book(book));
    }

    @GetMapping("/api/public/website/finance/{year}")
    public Map<String, Object> publicSummary(@PathVariable int year, @RequestParam(defaultValue = "rahbar") String book) {
        return finance.publicSummary(FinanceService.book(book), year);
    }
}
