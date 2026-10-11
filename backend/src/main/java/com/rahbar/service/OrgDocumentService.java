package com.rahbar.service;

import com.rahbar.entity.OrgDocument;
import com.rahbar.entity.OrgDocumentCategory;
import com.rahbar.entity.OrgDocumentRevision;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.OrgDocumentCategoryRepository;
import com.rahbar.repository.OrgDocumentRepository;
import com.rahbar.repository.OrgDocumentRevisionRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Admin > Documents: by-laws and other organisation documents kept as text, in categories. Saving a changed
 * document keeps the previous version, which can be viewed and restored. Documents are archived rather than
 * deleted; only the Super Admin can delete one.
 * <p>
 * Who may read a document is set on it: PUBLIC (every signed-in user, whatever the role) or ROLES (only the roles
 * chosen). People with "Documents" edit see every document, its earlier versions and archived ones.
 */
@Service
public class OrgDocumentService {

    /** About 2 MB of text: far more than any by-law. */
    static final int MAX_CONTENT = 2_000_000;

    private final OrgDocumentRepository documents;
    private final OrgDocumentCategoryRepository categories;
    private final OrgDocumentRevisionRepository revisions;
    private final UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.RoleRepository roles;

    public OrgDocumentService(OrgDocumentRepository documents, OrgDocumentCategoryRepository categories,
                              OrgDocumentRevisionRepository revisions, UserRepository users) {
        this.documents = documents;
        this.categories = categories;
        this.revisions = revisions;
        this.users = users;
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String text(Object v, String label, int max, boolean required) {
        String s = v == null ? null : String.valueOf(v).strip();
        if (s != null && s.isEmpty()) s = null;
        if (s == null && required) throw bad(label + " is required.");
        if (s != null && s.length() > max) throw bad(label + " is too long (at most " + max + " characters).");
        return s;
    }

    /** users.id -> name, for "last changed by". */
    private Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        Set<Long> wanted = new HashSet<>(ids);
        wanted.remove(null);
        if (!wanted.isEmpty()) users.findAllById(wanted).forEach(u -> out.put(u.getId(), u.getName()));
        return out;
    }

    // ---------------------------------------------------------------- who may read

    /** Adds and edits documents, and sees all of them. */
    static boolean editor() {
        return Access.can(com.rahbar.security.Section.DOCUMENTS, com.rahbar.security.Section.Level.EDIT);
    }

    /** May the signed-in user read the document? Editors: every one; others: public ones and their role's, not archived. */
    static boolean mayRead(OrgDocument d) {
        if (editor()) return true;
        if (d.isArchived()) return false;
        if ("PUBLIC".equals(d.getVisibility())) return true;
        Integer role = Access.current().getUser().getRoleId();
        return role != null && d.visibleRoles().contains(role);
    }

    /** role id -> name, for showing who may read a document. */
    private Map<Integer, String> roleNames() {
        Map<Integer, String> out = new HashMap<>();
        if (roles != null) roles.findAll().forEach(r -> out.put(r.getRoleId(), r.getRoleName()));
        return out;
    }

    /** "Everyone", or the role names (editors only see this). */
    private String readers(OrgDocument d, Map<Integer, String> names) {
        if ("PUBLIC".equals(d.getVisibility())) return "Everyone";
        List<String> list = d.visibleRoles().stream().map(id -> names.getOrDefault(id, "Role " + id)).toList();
        return list.isEmpty() ? "Only people who edit documents" : String.join(", ", list);
    }

    /** About 180 characters of the text around the first word found (empty when only the title matched). */
    static String snippet(String content, List<String> words) {
        if (content == null) return "";
        String lower = content.toLowerCase(Locale.ROOT);
        int at = -1;
        for (String w : words) {
            int i = lower.indexOf(w);
            if (i >= 0 && (at < 0 || i < at)) at = i;
        }
        if (at < 0) return "";
        int from = Math.max(0, at - 60), to = Math.min(content.length(), at + 120);
        String s = content.substring(from, to).replaceAll("\\s+", " ").strip();
        return (from > 0 ? "…" : "") + s + (to < content.length() ? "…" : "");
    }

    /** The roles to choose from when setting who may read a document. */
    public List<Map<String, Object>> roleOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (roles == null) return out;
        roles.findAll().stream().sorted(Comparator.comparing(com.rahbar.entity.Role::getRoleName, String.CASE_INSENSITIVE_ORDER))
                .forEach(r -> out.add(Map.of("id", r.getRoleId(), "name", r.getRoleName())));
        return out;
    }

    // ---------------------------------------------------------------- documents

    /** The documents the signed-in user may read (without their text, to keep the list small) and their categories. */
    public Map<String, Object> list() {
        return list(null);
    }

    /**
     * The documents the signed-in user may read; with a search, only those whose title or text contains every word of
     * it (any case), each with a short snippet of the text around the first match.
     */
    public Map<String, Object> list(String q) {
        List<String> words = q == null ? List.of() : Arrays.stream(q.toLowerCase(Locale.ROOT).split("\\s+")).filter(w -> !w.isBlank()).toList();
        List<OrgDocument> all = documents.findAllByOrderByTitleAsc().stream().filter(OrgDocumentService::mayRead)
                .filter(d -> words.isEmpty() || words.stream().allMatch(w -> (d.getTitle() + "\n" + d.getContent()).toLowerCase(Locale.ROOT).contains(w)))
                .toList();
        boolean editor = editor();
        Map<Integer, String> names = editor ? roleNames() : Map.of();
        Map<Long, String> who = names(all.stream().map(OrgDocument::getUpdatedBy).toList());
        Map<Long, Long> versions = new HashMap<>();
        revisions.findAll().forEach(r -> versions.merge(r.getDocumentId(), 1L, Long::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrgDocument d : all) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", d.getId());
            r.put("category_id", d.getCategoryId());
            r.put("title", d.getTitle());
            r.put("effective_date", d.getEffectiveDate());
            r.put("archived", d.isArchived());
            r.put("updated_at", d.getUpdatedAt());
            r.put("updated_by_name", who.get(d.getUpdatedBy()));
            r.put("earlier_versions", versions.getOrDefault(d.getId(), 0L));
            r.put("length", d.getContent() == null ? 0 : d.getContent().length());
            if (!words.isEmpty()) r.put("snippet", snippet(d.getContent(), words));
            if (editor) {
                r.put("visibility", d.getVisibility());
                r.put("readers", readers(d, names));
            } else {
                r.remove("earlier_versions");
            }
            rows.add(r);
        }
        Set<Long> used = new HashSet<>();
        all.forEach(d -> used.add(d.getCategoryId()));
        List<Map<String, Object>> cats = categoryRows().stream().filter(c -> editor || used.contains((Long) c.get("id"))).toList();
        return Map.of("documents", rows, "categories", cats, "editor", editor);
    }

    public Map<String, Object> get(Long id) {
        OrgDocument d = require(id);
        if (!mayRead(d)) throw new ApiException(HttpStatus.NOT_FOUND, "Document not found.");
        Map<String, Object> r = Rows.of(d);
        if (editor()) {
            r.put("readers", readers(d, roleNames()));
            r.put("visible_roles", new ArrayList<>(d.visibleRoles()));
        } else {
            r.remove("visibility");
            r.remove("visible_role_ids");
        }
        Map<Long, String> who = names(List.of(Objects.requireNonNullElse(d.getCreatedBy(), 0L), Objects.requireNonNullElse(d.getUpdatedBy(), 0L)));
        r.put("created_by_name", who.get(d.getCreatedBy()));
        r.put("updated_by_name", who.get(d.getUpdatedBy()));
        r.put("category_name", categories.findById(d.getCategoryId()).map(OrgDocumentCategory::getName).orElse(null));
        return r;
    }

    private OrgDocument require(Long id) {
        return documents.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found."));
    }

    /** Body: { categoryId, title, content, effectiveDate }. When the title, text or date changes, the old version is kept. */
    @Transactional
    public OrgDocument save(Long id, Map<String, Object> body) {
        OrgDocument d = id == null ? new OrgDocument() : require(id);
        Long categoryId = body.get("categoryId") == null || String.valueOf(body.get("categoryId")).isBlank() ? null
                : Long.valueOf(String.valueOf(body.get("categoryId")));
        if (categoryId == null || !categories.existsById(categoryId)) throw bad("Choose the category.");
        String title = text(body.get("title"), "Title", 200, true);
        // the text is kept exactly as pasted (only trailing blank lines are dropped)
        Object raw = body.get("content");
        String content = raw == null ? null : String.valueOf(raw).replace("\r\n", "\n").stripTrailing();
        if (content == null || content.isBlank()) throw bad("Paste the text of the document.");
        if (content.length() > MAX_CONTENT) throw bad("The text is too long.");
        LocalDate effective;
        try {
            Object e = body.get("effectiveDate");
            effective = e == null || String.valueOf(e).isBlank() ? null : LocalDate.parse(String.valueOf(e).substring(0, 10));
        } catch (RuntimeException e) {
            throw bad("The effective date must be a date like 2026-01-15.");
        }
        if (d.getId() != null && (!d.getTitle().equals(title) || !d.getContent().equals(content) || !Objects.equals(d.getEffectiveDate(), effective))) {
            OrgDocumentRevision r = new OrgDocumentRevision();
            r.setDocumentId(d.getId());
            r.setTitle(d.getTitle());
            r.setContent(d.getContent());
            r.setEffectiveDate(d.getEffectiveDate());
            revisions.save(r);
        }
        d.setCategoryId(categoryId);
        d.setTitle(title);
        d.setContent(content);
        d.setEffectiveDate(effective);
        if (body.containsKey("visibility") || d.getId() == null) setReaders(d, body);
        return documents.save(d);
    }

    /** Body: visibility PUBLIC | ROLES (default PUBLIC), visibleRoleIds: [role ids] (for ROLES). */
    private void setReaders(OrgDocument d, Map<String, Object> body) {
        String v = body.get("visibility") == null ? "PUBLIC" : String.valueOf(body.get("visibility")).strip().toUpperCase(Locale.ROOT);
        if (!Set.of("PUBLIC", "ROLES").contains(v)) throw bad("Choose who can read the document.");
        d.setVisibility(v);
        if ("PUBLIC".equals(v)) {
            d.setVisibleRoleIds(null);
            return;
        }
        Set<Integer> known = new HashSet<>();
        if (roles != null) roles.findAll().forEach(r -> known.add(r.getRoleId()));
        Set<Integer> chosen = new LinkedHashSet<>();
        if (body.get("visibleRoleIds") instanceof Collection<?> list) {
            for (Object o : list) {
                Integer id;
                try {
                    id = Integer.valueOf(String.valueOf(o).strip());
                } catch (NumberFormatException e) {
                    throw bad("Unknown role.");
                }
                if (!known.isEmpty() && !known.contains(id)) throw bad("Unknown role.");
                chosen.add(id);
            }
        }
        if (chosen.isEmpty()) throw bad("Choose at least one role that can read the document, or make it visible to everyone.");
        d.setVisibleRoleIds(String.join(",", chosen.stream().map(String::valueOf).toList()));
    }

    @Transactional
    public void setArchived(Long id, boolean archived) {
        OrgDocument d = require(id);
        d.setArchived(archived);
        documents.save(d);
    }

    /** Only the Super Admin; everyone else archives. */
    @Transactional
    public void delete(Long id) {
        if (!Access.isSuperAdmin()) throw Access.forbidden("Only the Super Admin can delete a document. Archive it instead.");
        documents.delete(require(id)); // its earlier versions go with it
    }

    // ---------------------------------------------------------------- earlier versions

    /** Earlier versions, newest first: who replaced each one and when. */
    public List<Map<String, Object>> revisions(Long id) {
        require(id);
        List<OrgDocumentRevision> list = revisions.findByDocumentIdOrderByIdDesc(id);
        Map<Long, String> who = names(list.stream().map(OrgDocumentRevision::getCreatedBy).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrgDocumentRevision r : list) {
            Map<String, Object> row = Rows.of(r);
            row.put("replaced_by_name", who.get(r.getCreatedBy()));
            rows.add(row);
        }
        return rows;
    }

    /** Makes an earlier version the current one again (the current text is kept as a version too). */
    @Transactional
    public OrgDocument restore(Long id, Long revisionId) {
        OrgDocument d = require(id);
        OrgDocumentRevision r = revisions.findById(revisionId).filter(x -> x.getDocumentId().equals(id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version not found."));
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", d.getCategoryId());
        body.put("title", r.getTitle());
        body.put("content", r.getContent());
        body.put("effectiveDate", r.getEffectiveDate() == null ? null : r.getEffectiveDate().toString());
        return save(id, body);
    }

    // ---------------------------------------------------------------- categories

    public List<Map<String, Object>> categoryRows() {
        Map<Long, Long> count = new HashMap<>();
        documents.findAll().forEach(d -> count.merge(d.getCategoryId(), 1L, Long::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrgDocumentCategory c : categories.findAllByOrderByDisplayOrderAscNameAsc()) {
            Map<String, Object> r = Rows.of(c);
            r.put("documents", count.getOrDefault(c.getId(), 0L));
            rows.add(r);
        }
        return rows;
    }

    /** Body: { name, description, displayOrder }. */
    @Transactional
    public OrgDocumentCategory saveCategory(Long id, Map<String, Object> body) {
        OrgDocumentCategory c = id == null ? new OrgDocumentCategory()
                : categories.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        String name = text(body.get("name"), "Name", 100, true);
        if (id == null ? categories.existsByNameIgnoreCase(name) : categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw bad("There is already a category called " + name + ".");
        }
        c.setName(name);
        c.setDescription(text(body.get("description"), "Description", 500, false));
        Object order = body.get("displayOrder");
        try {
            c.setDisplayOrder(order == null || String.valueOf(order).isBlank() ? 0 : Integer.parseInt(String.valueOf(order).strip()));
        } catch (NumberFormatException e) {
            throw bad("Order must be a whole number.");
        }
        return categories.save(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        OrgDocumentCategory c = categories.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
        if (documents.existsByCategoryId(id)) throw bad(c.getName() + " still has documents. Move them to another category first.");
        categories.delete(c);
    }

}
