package com.rahbar.controller;

import com.rahbar.service.OrgDocumentService;
import com.rahbar.util.Rows;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin > Documents: by-laws and other documents kept as text, and their categories. Every signed-in user may list
 * and open the documents they are allowed to read (set on each document); adding and editing needs "Documents" edit.
 */
@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("denyAll()")
public class OrgDocumentController {

    private final OrgDocumentService service;

    public OrgDocumentController(OrgDocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.get(id);
    }

    /** The roles to choose from for "who can read". */
    @GetMapping("/role-options")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public List<Map<String, Object>> roleOptions() {
        return service.roleOptions();
    }

    /** Body: { categoryId, title, content, effectiveDate, visibility PUBLIC | ROLES, visibleRoleIds: [role ids] }. */
    @PostMapping
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Document saved.", "id", service.save(null, body).getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.save(id, body);
        return Map.of("message", "Document saved.", "id", id);
    }

    /** Body: { archived: true | false }. */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, String> archive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean archived = !Boolean.FALSE.equals(body.get("archived"));
        service.setArchived(id, archived);
        return Map.of("message", archived ? "Document archived." : "Document restored from the archive.");
    }

    /** Super Admin only (checked in the service). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, String> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Document deleted.");
    }

    @GetMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public List<Map<String, Object>> revisions(@PathVariable Long id) {
        return service.revisions(id);
    }

    @PostMapping("/{id}/revisions/{revisionId}/restore")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, String> restore(@PathVariable Long id, @PathVariable Long revisionId) {
        service.restore(id, revisionId);
        return Map.of("message", "The earlier version is the current one again (the replaced text was kept as a version).");
    }

    // ---------------------------------------------------------------- categories

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('DOCUMENT_CATEGORIES:EDIT')")
    public Map<String, Object> addCategory(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Category added.", "data", Rows.of(service.saveCategory(null, body)));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_CATEGORIES:EDIT')")
    public Map<String, Object> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Category saved.", "data", Rows.of(service.saveCategory(id, body)));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_CATEGORIES:EDIT')")
    public Map<String, String> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return Map.of("message", "Category deleted.");
    }
}
