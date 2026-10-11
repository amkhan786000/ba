package com.rahbar.website;

import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Section;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Admin > Website: gallery, photo uploads, page text, translations, earlier versions and the lists for dropdowns. */
@RestController
@RequestMapping("/api/admin/website")
public class WebsiteAdminContentController {

    static final long MAX_PHOTO = 10L * 1024 * 1024;
    static final long MAX_DOCUMENT = 20L * 1024 * 1024;
    private static final Sort GALLERY_ORDER = Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.desc("id"));
    /** Sections whose screens upload photos from a form (cover image, story photo, project image ...). */
    private static final List<Section> UPLOADERS = List.of(Section.WEBSITE_GALLERY, Section.WEBSITE_BLOG, Section.WEBSITE_STORIES,
            Section.WEBSITE_PROJECTS, Section.WEBSITE_NEWS, Section.WEBSITE_PAGES);

    private final WebGalleryImageRepository images;
    private final WebsiteImageStorage storage;
    private final WebContentRepository content;
    private final WebTranslationRepository translations;
    private final WebRevisionRepository revisionRepo;
    private final WebsiteRevisions revisions;
    private final WebNewsRepository news;
    private final WebStoryRepository stories;
    private final WebProjectRepository projects;
    private final ChapterRepository chapters;
    private final RccCenterRepository centers;
    private final UserRepository users;
    private final WebBlogCategoryRepository categories;

    public WebsiteAdminContentController(WebGalleryImageRepository images, WebsiteImageStorage storage, WebContentRepository content,
                                         WebTranslationRepository translations, WebRevisionRepository revisionRepo,
                                         WebsiteRevisions revisions, WebNewsRepository news, WebStoryRepository stories,
                                         WebProjectRepository projects, ChapterRepository chapters, RccCenterRepository centers,
                                         UserRepository users, WebBlogCategoryRepository categories) {
        this.images = images;
        this.storage = storage;
        this.content = content;
        this.translations = translations;
        this.revisionRepo = revisionRepo;
        this.revisions = revisions;
        this.news = news;
        this.stories = stories;
        this.projects = projects;
        this.chapters = chapters;
        this.centers = centers;
        this.users = users;
        this.categories = categories;
    }

    private static boolean anyWebsite(Section.Level level) {
        return UPLOADERS.stream().anyMatch(s -> WebsiteSupport.can(s, s == Section.WEBSITE_BLOG ? Section.Level.VIEW : level))
                || WebsiteSupport.can(Section.WEBSITE_DIRECTORY, Section.Level.VIEW)
                || WebsiteSupport.can(Section.WEBSITE_MESSAGES, Section.Level.VIEW);
    }

    // ---- lists for dropdowns -----------------------------------------------------------------------------------

    public record Option(Long id, String name) {
    }

    public record ImageOption(Long id, String title, String album, String url) {
    }

    /** Projects, chapters, RCC centers and photos, for the dropdowns of every website screen. */
    @GetMapping("/lookups")
    @Transactional(readOnly = true)
    public Map<String, Object> lookups() {
        if (!anyWebsite(Section.Level.VIEW)) throw com.rahbar.security.Access.forbidden("You do not have permission for this.");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projects", projects.findAll(Sort.by("displayOrder", "name")).stream().map(p -> new Option(p.getId(), p.getName())).toList());
        out.put("chapters", chapters.findAllByOrderByChapterNameAsc().stream().map(c -> new Option(c.getChapterId(), c.getChapterName())).toList());
        out.put("centers", centers.findAll().stream().map(c -> new Option(c.getRccCenterId(), c.getCenterName()))
                .sorted(Comparator.comparing(o -> o.name() == null ? "" : o.name(), String.CASE_INSENSITIVE_ORDER)).toList());
        out.put("categories", categories.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(c -> new Option(c.getId(), c.isActive() ? c.getName() : c.getName() + " (hidden)")).toList());
        out.put("images", images.findAll(GALLERY_ORDER).stream().map(g -> new ImageOption(g.getId(), g.getTitle(), g.getAlbum(), g.getUrl())).toList());
        return out;
    }

    // ---- gallery -----------------------------------------------------------------------------------------------

    @GetMapping("/gallery")
    @Transactional(readOnly = true)
    public List<WebGalleryImage> gallery() {
        WebsiteSupport.require(Section.WEBSITE_GALLERY, Section.Level.VIEW);
        return images.findAll(GALLERY_ORDER);
    }

    @PostMapping(value = "/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public WebGalleryImage upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam String title,
                                  @RequestParam(required = false) String caption,
                                  @RequestParam(required = false) String album,
                                  @RequestParam(required = false) LocalDate takenOn,
                                  @RequestParam(defaultValue = "true") boolean published) {
        WebsiteSupport.require(Section.WEBSITE_GALLERY, Section.Level.EDIT);
        String t = WebsiteSupport.text(title, "Title", 200, true);
        var stored = storage.store(file, MAX_PHOTO);
        WebGalleryImage img = new WebGalleryImage();
        img.setTitle(t);
        img.setCaption(WebsiteSupport.text(caption, "Caption", 1000, false));
        img.setAlbum(WebsiteSupport.text(album, "Album", 80, false));
        img.setTakenOn(takenOn);
        img.setPublished(published);
        img.setFileName(stored.fileName());
        img.setContentType(stored.contentType());
        img.setSizeBytes(stored.size());
        try {
            return images.save(img);
        } catch (RuntimeException e) {
            storage.delete(stored.fileName());
            throw e;
        }
    }

    public record ImageDetails(String title, String caption, String album, LocalDate takenOn, Integer displayOrder, boolean published) {
    }

    /** The text and visibility; the file itself cannot be changed (upload a new one instead). */
    @PutMapping("/gallery/{id}")
    @Transactional
    public WebGalleryImage updateImage(@PathVariable Long id, @RequestBody ImageDetails d) {
        WebsiteSupport.require(Section.WEBSITE_GALLERY, Section.Level.EDIT);
        WebGalleryImage img = images.findById(id).orElseThrow(() -> WebsiteSupport.notFound("Photo"));
        img.setTitle(WebsiteSupport.text(d.title(), "Title", 200, true));
        img.setCaption(WebsiteSupport.text(d.caption(), "Caption", 1000, false));
        img.setAlbum(WebsiteSupport.text(d.album(), "Album", 80, false));
        img.setTakenOn(d.takenOn());
        img.setDisplayOrder(d.displayOrder() == null ? 0 : d.displayOrder());
        img.setPublished(d.published());
        return images.save(img);
    }

    @DeleteMapping("/gallery/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteImage(@PathVariable Long id) {
        WebsiteSupport.require(Section.WEBSITE_GALLERY, Section.Level.EDIT);
        WebGalleryImage img = images.findById(id).orElseThrow(() -> WebsiteSupport.notFound("Photo"));
        images.delete(img);
        images.flush();
        storage.delete(img.getFileName());
    }

    /**
     * A photo uploaded from a form (blog cover, story photo, project image ...). Kept out of the public gallery
     * (album "Uploads"); returns its URL.
     */
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, String> photo(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String title) {
        if (!anyWebsite(Section.Level.EDIT)) throw com.rahbar.security.Access.forbidden("You do not have permission for this.");
        var stored = storage.store(file, MAX_PHOTO);
        WebGalleryImage img = new WebGalleryImage();
        String t = WebsiteSupport.text(title, "Title", 200, false);
        img.setTitle(t == null ? "Uploaded photo" : t);
        img.setAlbum("Uploads");
        img.setPublished(false);
        img.setFileName(stored.fileName());
        img.setContentType(stored.contentType());
        img.setSizeBytes(stored.size());
        images.save(img);
        return Map.of("url", img.getUrl());
    }

    /** A PDF for a post (research paper, report): kept as a file only, returned as /api/documents/<file>. */
    @PostMapping(value = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> document(@RequestParam("file") MultipartFile file) {
        WebsiteSupport.require(Section.WEBSITE_BLOG, Section.Level.VIEW);
        var stored = storage.storeDocument(file, MAX_DOCUMENT);
        String name = file.getOriginalFilename() == null ? "Download" : file.getOriginalFilename().replaceAll("(?i)\\.pdf$", "").replaceAll("[\\r\\n]", " ").strip();
        return Map.of("url", "/api/documents/" + stored.fileName(), "name", name.isEmpty() ? "Download" : name.substring(0, Math.min(200, name.length())));
    }

    // ---- page text -----------------------------------------------------------------------------------------------

    public record ContentRow(String key, String lang, String value, String changedBy, LocalDateTime changedAt) {
    }

    public record ContentEdit(String key, String lang, String value) {
    }

    @GetMapping("/content")
    @Transactional(readOnly = true)
    public List<ContentRow> content() {
        WebsiteSupport.require(Section.WEBSITE_PAGES, Section.Level.VIEW);
        List<WebContent> rows = content.findAll();
        Map<Long, String> names = names(rows);
        return rows.stream().map(c -> new ContentRow(c.getKey(), c.getLang(), c.getValue(), names.get(c.getUpdatedBy()), c.getUpdatedAt())).toList();
    }

    /** Saves one text; an empty value removes the change, so the website's built-in text shows again. */
    @PutMapping("/content")
    @Transactional
    public ContentRow saveContent(@RequestBody ContentEdit e) {
        WebsiteSupport.require(Section.WEBSITE_PAGES, Section.Level.EDIT);
        String key = WebsiteSupport.text(e.key(), "Key", 100, true);
        if (!key.matches("[a-z0-9][a-z0-9._-]*")) throw WebsiteSupport.bad("Invalid key.");
        WebsiteLang lang = WebsiteLang.require(e.lang());
        if (e.value() != null && e.value().length() > 200_000) throw WebsiteSupport.bad("The text is too long.");
        var existing = content.findByKeyAndLang(key, lang.code());
        String value = e.value() == null || e.value().isBlank() ? null : e.value();
        revisions.saved(WebsiteRevisions.site(key), lang, existing.map(WebContent::getValue).orElse(null), value);
        if (value == null) {
            existing.ifPresent(content::delete);
            return new ContentRow(key, lang.code(), null, null, null);
        }
        WebContent c = existing.orElseGet(WebContent::new);
        c.setKey(key);
        c.setLang(lang.code());
        c.setValue(value);
        content.saveAndFlush(c);
        return new ContentRow(key, lang.code(), value, WebsiteSupport.me().getName(), c.getUpdatedAt());
    }

    // ---- translations of records -----------------------------------------------------------------------------

    public record TranslationRow(String lang, String field, String value, String changedBy, LocalDateTime changedAt) {
    }

    public record TranslationEdit(String type, Long id, String lang, String field, String value) {
    }

    @GetMapping("/translations")
    @Transactional(readOnly = true)
    public List<TranslationRow> translations(@RequestParam String type, @RequestParam Long id) {
        WebsiteTranslatable t = check(type, id, Section.Level.VIEW);
        List<WebTranslation> rows = translations.findByEntityTypeAndEntityId(t.name(), id);
        Map<Long, String> names = names(rows);
        return rows.stream().map(x -> new TranslationRow(x.getLang(), x.getField(), x.getValue(), names.get(x.getUpdatedBy()), x.getUpdatedAt())).toList();
    }

    /** Saves one field in one language; an empty value removes it (English is shown again). */
    @PutMapping("/translations")
    @Transactional
    public TranslationRow saveTranslation(@RequestBody TranslationEdit e) {
        WebsiteTranslatable t = check(e.type(), e.id(), Section.Level.EDIT);
        WebsiteLang lang = WebsiteLang.require(e.lang());
        if (lang == WebsiteLang.EN) throw WebsiteSupport.bad("English is edited on the record itself.");
        if (!t.fields().contains(e.field())) throw WebsiteSupport.bad("Field " + e.field() + " cannot be translated.");
        if (e.value() != null && e.value().length() > 200_000) throw WebsiteSupport.bad("The text is too long.");
        var existing = translations.findByEntityTypeAndEntityIdAndLangAndField(t.name(), e.id(), lang.code(), e.field());
        String value = e.value() == null || e.value().isBlank() ? null : e.value();
        revisions.saved(WebsiteRevisions.translation(t, e.id(), e.field()), lang, existing.map(WebTranslation::getValue).orElse(null), value);
        if (value == null) {
            existing.ifPresent(translations::delete);
            return new TranslationRow(lang.code(), e.field(), null, null, null);
        }
        WebTranslation x = existing.orElseGet(WebTranslation::new);
        x.setEntityType(t.name());
        x.setEntityId(e.id());
        x.setLang(lang.code());
        x.setField(e.field());
        x.setValue(value);
        translations.saveAndFlush(x);
        return new TranslationRow(lang.code(), e.field(), value, WebsiteSupport.me().getName(), x.getUpdatedAt());
    }

    /** The record's section decides; chapter / RCC-scoped users only reach their own news and stories. */
    private WebsiteTranslatable check(String type, Long id, Section.Level level) {
        WebsiteTranslatable t = WebsiteTranslatable.of(type).orElseThrow(() -> WebsiteSupport.bad("Unknown type " + type + "."));
        WebsiteSupport.require(t.section(), level);
        if (id == null) throw WebsiteSupport.bad("id is required.");
        Long chapter = WebsiteSupport.scopedChapter();
        if (t == WebsiteTranslatable.NEWS) {
            WebNews n = news.findById(id).orElseThrow(() -> WebsiteSupport.notFound("News item"));
            if (chapter != null && !Objects.equals(n.getChapterId(), chapter)) throw WebsiteSupport.notFound("News item");
        } else if (t == WebsiteTranslatable.STORY) {
            WebStory s = stories.findById(id).orElseThrow(() -> WebsiteSupport.notFound("Success story"));
            if (!WebsiteAdminPostsController.Stories.inScope(s)) throw WebsiteSupport.notFound("Success story");
        }
        return t;
    }

    // ---- earlier versions ----------------------------------------------------------------------------------------

    public record Version(Long id, String value, String savedBy, LocalDateTime savedAt) {
    }

    /** Newest first. To restore one, the screen puts it back in the editor and saves it (a new version). */
    @GetMapping("/revisions")
    @Transactional(readOnly = true)
    public List<Version> revisions(@RequestParam String target, @RequestParam(defaultValue = "en") String lang) {
        WebsiteSupport.require(sectionOf(target), Section.Level.VIEW);
        List<WebRevision> rows = revisionRepo.findTop30ByTargetAndLangOrderByIdDesc(target, WebsiteLang.require(lang).code());
        Map<Long, String> names = names(rows);
        return rows.stream().map(r -> new Version(r.getId(), r.getValue(), names.get(r.getCreatedBy()), r.getCreatedAt())).toList();
    }

    /** site:... is page text; rec:TYPE:... and tr:TYPE:... belong to that record type's section. */
    static Section sectionOf(String target) {
        if (target == null) throw WebsiteSupport.bad("target is required.");
        if (target.startsWith("site:")) return Section.WEBSITE_PAGES;
        String[] parts = target.split(":");
        if (parts.length >= 2 && (parts[0].equals("rec") || parts[0].equals("tr"))) {
            return WebsiteTranslatable.of(parts[1]).map(WebsiteTranslatable::section)
                    .orElseThrow(() -> WebsiteSupport.bad("Unknown target " + target + "."));
        }
        throw WebsiteSupport.bad("Unknown target " + target + ".");
    }

    private Map<Long, String> names(Collection<? extends com.rahbar.entity.Modifiable> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> {
            if (r.getCreatedBy() != null) ids.add(r.getCreatedBy());
            if (r.getUpdatedBy() != null) ids.add(r.getUpdatedBy());
        });
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) users.findAllById(ids).forEach(u -> out.put(u.getId(), u.getName()));
        return out;
    }
}
