package com.rahbar.controller;

import com.rahbar.repository.WebTranslationRepository;
import com.rahbar.controller.WebsiteCrud;
import com.rahbar.entity.WebsiteLang;
import com.rahbar.service.WebsiteRevisions;
import com.rahbar.util.WebsiteSupport;
import com.rahbar.entity.WebsiteTranslatable;

import com.rahbar.entity.Modifiable;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Section;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;

/**
 * Create / read / update / delete for one kind of website record in Admin > Website. VIEW on the section lists and
 * opens records, EDIT changes them. Subclasses add checks, chapter scoping and the texts whose versions are kept.
 * Records are sent as JSON with their audit fields; the list also carries the names of the people who made changes.
 */
public abstract class WebsiteCrud<T extends Modifiable> {

    private static final String[] NOT_COPIED = {"id", "createdBy", "updatedBy", "createdAt", "updatedAt"};

    private final JpaRepository<T, Long> repository;
    private final Section section;
    private final String label;
    private final Function<T, Long> idOf;
    @Autowired
    private WebsiteRevisions revisions;
    @Autowired
    private UserRepository users;

    protected WebsiteCrud(JpaRepository<T, Long> repository, Section section, String label, Function<T, Long> idOf) {
        this.repository = repository;
        this.section = section;
        this.label = label;
        this.idOf = idOf;
    }

    // ---- hooks -----------------------------------------------------------------------------------------------

    /** Required fields and other rules, with messages for the person editing. */
    protected abstract void validate(T entity);

    /** Records created by visitors (messages) cannot be created here. */
    protected boolean creatable() {
        return true;
    }

    /** May the signed-in user see and change this record (chapter-scoped users: only their chapter's)? */
    protected boolean inScope(T entity) {
        return true;
    }

    /** Pins a new or edited record to the user's scope (e.g. sets their chapter). */
    protected void applyScope(T entity) {
    }

    /** Record type whose English texts keep a version history and have translations (null = none). */
    protected WebsiteTranslatable type() {
        return null;
    }

    protected Map<String, Function<T, String>> revisionFields() {
        return Map.of();
    }

    /** Order of the list (default: newest first). */
    protected Sort listOrder() {
        return Sort.by(Sort.Direction.DESC, "id");
    }

    @Autowired
    private WebTranslationRepository translations;

    // ---- endpoints ---------------------------------------------------------------------------------------------

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        WebsiteSupport.require(section, Section.Level.VIEW);
        List<T> rows = repository.findAll(listOrder()).stream().filter(this::inScope).toList();
        return Map.of("data", rows, "names", names(rows));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public T get(@PathVariable Long id) {
        WebsiteSupport.require(section, Section.Level.VIEW);
        return find(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public T create(@RequestBody T body) {
        WebsiteSupport.require(section, Section.Level.EDIT);
        if (!creatable()) throw WebsiteSupport.bad(label + " records come from the website and cannot be added here.");
        body.setCreatedBy(null);
        body.setUpdatedBy(null);
        setId(body, null);
        applyScope(body);
        validate(body);
        T saved = repository.save(body);
        keepVersions(saved, Map.of());
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    public T update(@PathVariable Long id, @RequestBody T body) {
        WebsiteSupport.require(section, Section.Level.EDIT);
        T existing = find(id);
        Map<String, String> before = new HashMap<>();
        revisionFields().forEach((f, getter) -> before.put(f, getter.apply(existing)));
        BeanUtils.copyProperties(body, existing, NOT_COPIED);
        applyScope(existing);
        validate(existing);
        T saved = repository.save(existing);
        keepVersions(saved, before);
        return saved;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        WebsiteSupport.require(section, Section.Level.EDIT);
        T existing = find(id);
        if (type() != null) translations.deleteAllFor(type().name(), idOf.apply(existing));
        repository.delete(existing);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    protected T find(Long id) {
        return repository.findById(id).filter(this::inScope).orElseThrow(() -> WebsiteSupport.notFound(label));
    }

    private void keepVersions(T saved, Map<String, String> before) {
        if (type() == null) return;
        revisionFields().forEach((field, getter) -> revisions.saved(
                WebsiteRevisions.record(type(), idOf.apply(saved), field), WebsiteLang.EN, before.get(field), getter.apply(saved)));
    }

    /** users.id -> name of everyone who created or last changed one of the rows. */
    protected Map<Long, String> names(Collection<? extends Modifiable> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> {
            if (r.getCreatedBy() != null) ids.add(r.getCreatedBy());
            if (r.getUpdatedBy() != null) ids.add(r.getUpdatedBy());
        });
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) users.findAllById(ids).forEach(u -> out.put(u.getId(), u.getName()));
        return out;
    }

    private static void setId(Object entity, Long id) {
        try {
            entity.getClass().getMethod("setId", Long.class).invoke(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
