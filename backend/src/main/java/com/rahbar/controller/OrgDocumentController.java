package com.rahbar.controller;

import com.rahbar.service.OrgDocumentService;
import com.rahbar.util.Rows;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Admin > Documents: by-laws and other documents (text and / or an attached PDF / Word file), and their categories. Every signed-in user may list
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
    /** asRole: the role of the portal the list is shown in (sponsor, student, ...): only what that role may read. */
    public Map<String, Object> list(@RequestParam(required = false) String q, @RequestParam(required = false) Integer asRole) {
        return service.list(q, asRole);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable Long id, @RequestParam(required = false) Integer asRole) {
        return service.get(id, asRole);
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

    /** With a file: "data" (the JSON body above, plus removeFile) and "file" (a PDF / Word file to attach, optional). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, Object> addWithFile(@RequestPart("data") Map<String, Object> body,
                                           @RequestPart(value = "file", required = false) MultipartFile file) {
        return Map.of("message", "Document saved.", "id", service.save(null, body, file).getId());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public Map<String, Object> updateWithFile(@PathVariable Long id, @RequestPart("data") Map<String, Object> body,
                                              @RequestPart(value = "file", required = false) MultipartFile file) {
        service.save(id, body, file);
        return Map.of("message", "Document saved.", "id", id);
    }

    /** The attached file, for whoever may read the document: a PDF opens in the browser, Word files download. */
    @GetMapping("/{id}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FileSystemResource> file(@PathVariable Long id, @RequestParam(required = false) Integer asRole) {
        return send(service.file(id, asRole));
    }

    @GetMapping("/{id}/revisions/{revisionId}/file")
    @PreAuthorize("hasAuthority('DOCUMENTS:EDIT')")
    public ResponseEntity<FileSystemResource> revisionFile(@PathVariable Long id, @PathVariable Long revisionId) {
        return send(service.revisionFile(id, revisionId));
    }

    private static ResponseEntity<FileSystemResource> send(OrgDocumentService.Attachment a) {
        ContentDisposition.Builder b = "application/pdf".equals(a.contentType()) ? ContentDisposition.inline() : ContentDisposition.attachment();
        // a plain name as it is (an encoded one only when it has other characters, e.g. Urdu)
        boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(a.name());
        ContentDisposition how = (ascii ? b.filename(a.name()) : b.filename(a.name(), StandardCharsets.UTF_8)).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, how.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(a.path()));
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
