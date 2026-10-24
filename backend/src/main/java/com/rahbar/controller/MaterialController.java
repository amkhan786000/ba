package com.rahbar.controller;

import com.rahbar.service.MaterialService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Community > Materials: items, stock received and hand-outs to students. */
@RestController
@RequestMapping("/api/admin/materials")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('MATERIALS:VIEW')")
    public List<Map<String, Object>> items() {
        return service.items();
    }

    /** Body: { name, category, unit, notes, active }. */
    @PostMapping("/items")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, Object> addItem(@RequestBody Map<String, Object> body) {
        return service.saveItem(null, body);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, Object> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.saveItem(id, body);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, String> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return Map.of("message", "Item deleted.");
    }

    @GetMapping("/items/{id}/stock")
    @PreAuthorize("hasAuthority('MATERIALS:VIEW')")
    public List<Map<String, Object>> receipts(@PathVariable Long id) {
        return service.receipts(id);
    }

    /** Body: { itemId, quantity, receivedOn, donorName, donorUserId, note }. */
    @PostMapping("/stock")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, Object> addReceipt(@RequestBody Map<String, Object> body) {
        return service.addReceipt(body);
    }

    @DeleteMapping("/stock/{id}")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, String> deleteReceipt(@PathVariable Long id) {
        service.deleteReceipt(id);
        return Map.of("message", "Stock entry removed.");
    }

    @GetMapping("/given")
    @PreAuthorize("hasAuthority('MATERIALS:VIEW')")
    public List<Map<String, Object>> distributions(@RequestParam(required = false) Long itemId, @RequestParam(required = false) String kind,
                                                   @RequestParam(required = false) String q) {
        return service.distributions(itemId, kind, q);
    }

    /** Body: { itemId, quantity (each), givenOn, note, recipients: [{ kind: SCHOLAR | COACHING | REB, id }] }. */
    @PostMapping("/given")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, Object> give(@RequestBody Map<String, Object> body) {
        return service.give(body);
    }

    @DeleteMapping("/given/{id}")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public Map<String, String> deleteDistribution(@PathVariable Long id) {
        service.deleteDistribution(id);
        return Map.of("message", "Hand-out removed (back in stock).");
    }

    @GetMapping("/recipients")
    @PreAuthorize("hasAuthority('MATERIALS:EDIT')")
    public List<Map<String, Object>> recipients(@RequestParam(required = false) String q) {
        return service.searchRecipients(q);
    }
}
