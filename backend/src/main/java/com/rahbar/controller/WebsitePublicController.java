package com.rahbar.controller;

import com.rahbar.dto.PageBody;

import com.rahbar.entity.WebBlogCategory;
import com.rahbar.repository.WebBlogCategoryRepository;
import com.rahbar.entity.WebBlogPost;
import com.rahbar.repository.WebBlogPostRepository;
import com.rahbar.entity.WebContent;
import com.rahbar.repository.WebContentRepository;
import com.rahbar.entity.WebGalleryImage;
import com.rahbar.repository.WebGalleryImageRepository;
import com.rahbar.entity.WebMember;
import com.rahbar.repository.WebMemberRepository;
import com.rahbar.entity.WebNews;
import com.rahbar.repository.WebNewsRepository;
import com.rahbar.entity.WebPage;
import com.rahbar.repository.WebPageRepository;
import com.rahbar.entity.WebProject;
import com.rahbar.repository.WebProjectRepository;
import com.rahbar.entity.WebStory;
import com.rahbar.repository.WebStoryRepository;
import com.rahbar.entity.WebVolunteerOption;
import com.rahbar.repository.WebVolunteerOptionRepository;
import com.rahbar.service.WebsiteImageStorage;
import com.rahbar.controller.WebsiteImagesController;
import com.rahbar.entity.WebsiteLang;
import com.rahbar.controller.WebsitePublicController;
import com.rahbar.util.WebsiteSupport;
import com.rahbar.entity.WebsiteTranslatable;
import com.rahbar.service.WebsiteTranslations;

import com.rahbar.service.PublicDirectoryService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.rahbar.dto.WebsiteProjectView;
import com.rahbar.dto.WebsiteNewsView;
import com.rahbar.dto.WebsiteCategoryView;
import com.rahbar.dto.WebsitePostView;
import com.rahbar.dto.WebsiteCategoryRef;
import com.rahbar.dto.WebsiteImageView;
import com.rahbar.dto.WebsiteStoryView;
import com.rahbar.dto.WebsiteDirectoryEntry;
import com.rahbar.dto.WebsiteStats;
import com.rahbar.dto.WebsiteMenuItem;
import com.rahbar.dto.WebsitePageView;
import com.rahbar.dto.WebsiteVolunteerOptionView;
import com.rahbar.dto.PublicChapter;
import com.rahbar.dto.PublicCenter;

/**
 * Everything the Bihar Anjuman website (biharanjuman.com) reads. The website's nginx forwards its {@code /api/...}
 * here unchanged ({@code /api/blog} becomes {@code /api/public/website/blog}), so these paths and JSON shapes are the
 * website's API. Only published content, and only public fields: no audit data, no directory contact details
 * unless allowed, no full names on stories unless allowed. GETs accept {@code ?lang=ur|hi} where text is translated.
 */
@RestController
@RequestMapping("/api/public/website")
@Transactional(readOnly = true)
public class WebsitePublicController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.RebStudentRepository rebStudents;

    public enum BlogSort { NEWEST, OLDEST, TITLE, TOPIC }

    private static final Sort GALLERY_ORDER = Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.desc("id"));
    private static final CacheControl SHORT = CacheControl.maxAge(Duration.ofSeconds(30));

    private final PublicDirectoryService directory;
    private final WebProjectRepository projects;
    private final WebNewsRepository news;
    private final WebBlogPostRepository posts;
    private final WebStoryRepository stories;
    private final WebGalleryImageRepository images;
    private final WebMemberRepository members;
    private final WebPageRepository pages;
    private final WebContentRepository content;
    private final WebBlogCategoryRepository categories;
    private final WebsiteTranslations i18n;
    private final WebsiteImageStorage storage;

    public WebsitePublicController(PublicDirectoryService directory, WebProjectRepository projects, WebNewsRepository news,
                                   WebBlogPostRepository posts, WebStoryRepository stories, WebGalleryImageRepository images,
                                   WebMemberRepository members, WebPageRepository pages, WebContentRepository content,
                                   WebsiteTranslations i18n, WebsiteImageStorage storage, WebBlogCategoryRepository categories) {
        this.directory = directory;
        this.projects = projects;
        this.news = news;
        this.posts = posts;
        this.stories = stories;
        this.images = images;
        this.members = members;
        this.pages = pages;
        this.content = content;
        this.i18n = i18n;
        this.storage = storage;
        this.categories = categories;
    }

    // ---- chapters, centers and numbers (Rahbar's own data) ------------------------------------------------

    @GetMapping("/chapters")
    public List<PublicChapter> chapters() {
        return directory.chapters();
    }

    @GetMapping("/chapters/{id}")
    public PublicChapter chapter(@PathVariable Long id) {
        return directory.chapters().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> WebsiteSupport.notFound("Chapter"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private WebVolunteerOptionRepository volunteerOptions;

    /** The active ways to help on "Volunteer with us", in their order (Admin > Volunteers > Form options). */
    @GetMapping("/volunteer-options")
    public List<WebsiteVolunteerOptionView> volunteerOptions(@RequestParam(required = false) String lang) {
        return i18n.apply(WebsiteTranslatable.VOLUNTEER_OPTION, WebsiteLang.of(lang),
                        volunteerOptions.findByActiveTrueOrderByDisplayOrderAscIdAsc(), WebVolunteerOption::getId, WebsiteTranslations.VOLUNTEER_OPTION)
                .stream()
                .map(o -> new WebsiteVolunteerOptionView(o.getOptionKey(), o.getKind().name(), o.getTitle(), o.getDescription(),
                        o.getChoicesLabel(), WebVolunteerOption.lines(o.getChoices()), o.getExtraLabel(), WebVolunteerOption.lines(o.getExtraChoices())))
                .toList();
    }

    @GetMapping("/coaching-centers")
    public List<PublicCenter> coachingCenters() {
        return directory.centers();
    }

    @GetMapping("/stats")
    public WebsiteStats stats() {
        return new WebsiteStats(directory.chapters().size(), projects.countByActiveTrue(), directory.centers().size(),
                directory.stats().students(), members.countByApprovedTrue(), rebStudents.countByStatus("ACTIVE"));
    }

    // ---- projects and news ---------------------------------------------------------------------------------

    @GetMapping("/projects")
    public List<WebsiteProjectView> projects(@RequestParam(required = false) String lang) {
        return i18n.apply(WebsiteTranslatable.PROJECT, WebsiteLang.of(lang), projects.findByActiveTrueOrderByDisplayOrderAscNameAsc(),
                WebProject::getId, WebsiteTranslations.PROJECT).stream().map(WebsiteProjectView::of).toList();
    }

    @GetMapping("/projects/{slug}")
    public WebsiteProjectView project(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebProject p = projects.findBySlugAndActiveTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Project"));
        return WebsiteProjectView.of(i18n.apply(WebsiteTranslatable.PROJECT, WebsiteLang.of(lang), p, WebProject::getId, WebsiteTranslations.PROJECT));
    }

    @GetMapping("/news")
    public PageBody<WebsiteNewsView> news(@RequestParam(required = false) WebNews.Kind kind,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String lang,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("createdAt")));
        Specification<WebNews> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("published")));
            if (kind != null) where.add(cb.equal(root.get("kind"), kind));
            if (WebsiteSupport.present(q)) where.add(WebsiteSupport.anyFieldContains(root, cb, q, "title", "summary", "content", "location"));
            return cb.and(where.toArray(Predicate[]::new));
        };
        var result = news.findAll(spec, pageable);
        i18n.apply(WebsiteTranslatable.NEWS, WebsiteLang.of(lang), result.getContent(), WebNews::getId, WebsiteTranslations.NEWS);
        return PageBody.of(result.map(WebsiteNewsView::of));
    }

    @GetMapping("/news/{slug}")
    public WebsiteNewsView newsItem(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebNews n = news.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("News item"));
        return WebsiteNewsView.of(i18n.apply(WebsiteTranslatable.NEWS, WebsiteLang.of(lang), n, WebNews::getId, WebsiteTranslations.NEWS));
    }

    // ---- blog ----------------------------------------------------------------------------------------------

    /** Active categories (Knowledge Hub sections), in their order, with the number of published posts in each. */
    @GetMapping("/blog/categories")
    public List<WebsiteCategoryView> blogCategories(@RequestParam(required = false) String lang) {
        List<WebBlogCategory> list = i18n.apply(WebsiteTranslatable.CATEGORY, WebsiteLang.of(lang),
                categories.findByActiveTrueOrderByDisplayOrderAscNameAsc(), WebBlogCategory::getId, WebsiteTranslations.CATEGORY);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : posts.countPublishedByCategory()) counts.put((Long) row[0], (Long) row[1]);
        return list.stream().map(c -> new WebsiteCategoryView(c.getId(), c.getSlug(), c.getName(), c.getDescription(),
                counts.getOrDefault(c.getId(), 0L))).toList();
    }

    /** slug -> name of the active categories (names in the visitor's language). */
    private Map<Long, WebsiteCategoryRef> categoryRefs(String lang) {
        Map<Long, WebsiteCategoryRef> out = new HashMap<>();
        i18n.apply(WebsiteTranslatable.CATEGORY, WebsiteLang.of(lang), categories.findByActiveTrueOrderByDisplayOrderAscNameAsc(),
                WebBlogCategory::getId, WebsiteTranslations.CATEGORY).forEach(c -> out.put(c.getId(), new WebsiteCategoryRef(c.getSlug(), c.getName())));
        return out;
    }

    /** projectId 0 = posts not linked to a project ("Others"); category = a category's slug (Knowledge Hub section). */
    @GetMapping("/blog")
    public PageBody<WebsitePostView> blog(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) String topic,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String lang,
                                   @RequestParam(required = false) Long projectId,
                                   @RequestParam(defaultValue = "NEWEST") BlogSort sort,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "9") int size) {
        Sort order = switch (sort) {
            case NEWEST -> Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
            case OLDEST -> Sort.by(Sort.Order.asc("publishedAt"), Sort.Order.asc("id"));
            case TITLE -> Sort.by(Sort.Order.asc("title"));
            case TOPIC -> Sort.by(Sort.Order.asc("topic"), Sort.Order.desc("publishedAt"));
        };
        Specification<WebBlogPost> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("published")));
            if (topic != null && !topic.isBlank()) where.add(cb.equal(root.get("topic"), topic.strip()));
            if (category != null && !category.isBlank()) {
                Long id = categories.findBySlug(category.strip()).filter(WebBlogCategory::isActive).map(WebBlogCategory::getId).orElse(-1L);
                where.add(cb.equal(root.get("categoryId"), id));
            }
            if (projectId != null) {
                where.add(projectId == 0 ? cb.isNull(root.get("projectId")) : cb.equal(root.get("projectId"), projectId));
            }
            if (WebsiteSupport.present(q)) {
                where.add(WebsiteSupport.anyFieldContains(root, cb, q, "title", "summary", "topic", "author", "content", "subjectName"));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Map<Long, WebsiteCategoryRef> refs = categoryRefs(lang);
        return PageBody.of(posts.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), order)).map(p -> WebsitePostView.of(p, refs)));
    }

    @GetMapping("/blog/topics")
    public List<String> blogTopics() {
        return posts.publishedTopics();
    }

    @GetMapping("/blog/{slug}")
    public WebsitePostView post(@PathVariable String slug, @RequestParam(required = false) String lang) {
        return WebsitePostView.of(findPost(slug), categoryRefs(lang));
    }

    /** Other posts on the same topic, or else in the same category. */
    @GetMapping("/blog/{slug}/related")
    public List<WebsitePostView> related(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebBlogPost p = findPost(slug);
        Map<Long, WebsiteCategoryRef> refs = categoryRefs(lang);
        List<WebBlogPost> list = posts.findTop3ByPublishedTrueAndTopicAndIdNotOrderByPublishedAtDesc(p.getTopic(), p.getId());
        if (list.isEmpty() && p.getCategoryId() != null) {
            list = posts.findTop3ByPublishedTrueAndCategoryIdAndIdNotOrderByPublishedAtDesc(p.getCategoryId(), p.getId());
        }
        return list.stream().map(x -> WebsitePostView.of(x, refs)).toList();
    }

    private WebBlogPost findPost(String slug) {
        return posts.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Post"));
    }

    // ---- success stories -----------------------------------------------------------------------------------

    /** projectId 0 = stories not linked to a project ("Others"). */
    @GetMapping("/stories")
    public PageBody<WebsiteStoryView> stories(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Long projectId,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) String lang,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "12") int size) {
        Specification<WebStory> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("published")));
            if (projectId != null) {
                where.add(projectId == 0 ? cb.isNull(root.get("projectId")) : cb.equal(root.get("projectId"), projectId));
            }
            if (year != null) where.add(cb.equal(root.get("year"), year));
            if (WebsiteSupport.present(q)) where.add(WebsiteSupport.anyFieldContains(root, cb, q, "title", "summary", "story", "nowWhere"));
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50),
                Sort.by(Sort.Order.desc("year"), Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        var result = stories.findAll(spec, pageable);
        i18n.apply(WebsiteTranslatable.STORY, WebsiteLang.of(lang), result.getContent(), WebStory::getId, WebsiteTranslations.STORY);
        return PageBody.of(result.map(WebsiteStoryView::of));
    }

    @GetMapping("/stories/years")
    public List<Integer> storyYears() {
        return stories.publishedYears();
    }

    @GetMapping("/stories/{slug}")
    public WebsiteStoryView story(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebStory s = stories.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Success story"));
        return WebsiteStoryView.of(i18n.apply(WebsiteTranslatable.STORY, WebsiteLang.of(lang), s, WebStory::getId, WebsiteTranslations.STORY));
    }

    // ---- gallery and image files ---------------------------------------------------------------------------

    @GetMapping("/gallery")
    public PageBody<WebsiteImageView> gallery(@RequestParam(required = false) String album,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "24") int size) {
        Specification<WebGalleryImage> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("published")));
            if (album != null && !album.isBlank()) where.add(cb.equal(root.get("album"), album.strip()));
            if (WebsiteSupport.present(q)) where.add(WebsiteSupport.anyFieldContains(root, cb, q, "title", "caption", "album"));
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageBody.of(images.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), GALLERY_ORDER)).map(WebsiteImageView::of));
    }

    @GetMapping("/gallery/albums")
    public List<String> albums() {
        return images.publishedAlbums();
    }

    /**
     * An uploaded website photo (the website's /api/images/{file}). Unpublished photos stay reachable by URL: blog
     * covers and story photos are kept out of the gallery. See also WebsiteImagesController.
     */
    @GetMapping("/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        return serve(storage, fileName, false);
    }

    /** A PDF attached to a post (the website's /api/documents/{file}). */
    @GetMapping("/documents/{fileName}")
    public ResponseEntity<Resource> document(@PathVariable String fileName) {
        return serve(storage, fileName, true);
    }

    /** Photos at .../images/, PDFs at .../documents/ (each only its own kind). */
    static ResponseEntity<Resource> serve(WebsiteImageStorage storage, String fileName, boolean pdf) {
        Path path = fileName != null && fileName.endsWith(".pdf") == pdf ? storage.resolve(fileName) : null;
        if (path == null) throw WebsiteSupport.notFound(pdf ? "Document" : "Image");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(WebsiteImageStorage.contentTypeOf(fileName)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }

    // ---- directory -----------------------------------------------------------------------------------------

    @GetMapping("/directory")
    public PageBody<WebsiteDirectoryEntry> directory(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String country,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Specification<WebMember> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("approved")));
            if (country != null && !country.isBlank()) where.add(cb.equal(root.get("currentCountry"), country.strip()));
            if (WebsiteSupport.present(q)) {
                where.add(WebsiteSupport.anyFieldContains(root, cb, q, "fullName", "homeDistrict", "currentCountry",
                        "currentCity", "profession", "organization"));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("fullName"));
        return PageBody.of(members.findAll(spec, pageable).map(WebsiteDirectoryEntry::of));
    }

    @GetMapping("/directory/countries")
    public List<String> directoryCountries() {
        return members.approvedCountries();
    }

    // ---- page text and custom pages ------------------------------------------------------------------------

    /** Edited page text in English and in the requested language: {"en": {...}, "ur": {...}}. */
    @GetMapping("/content")
    public ResponseEntity<Map<String, Map<String, String>>> content(@RequestParam(required = false) String lang) {
        WebsiteLang l = WebsiteLang.of(lang);
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        out.put("en", new TreeMap<>());
        out.putIfAbsent(l.code(), new TreeMap<>());
        for (WebContent c : content.findByLangIn(new HashSet<>(List.of(WebsiteLang.EN.code(), l.code())))) {
            out.get(c.getLang()).put(c.getKey(), c.getValue());
        }
        return ResponseEntity.ok().cacheControl(SHORT).body(out);
    }

    @GetMapping("/pages")
    public List<WebsiteMenuItem> menu(@RequestParam(required = false) String lang) {
        return i18n.apply(WebsiteTranslatable.PAGE, WebsiteLang.of(lang), pages.findByPublishedTrueAndShowInMenuTrueOrderByMenuOrderAscTitleAsc(),
                WebPage::getId, WebsiteTranslations.PAGE).stream().map(p -> new WebsiteMenuItem(p.getSlug(), p.getTitle())).toList();
    }

    @GetMapping("/pages/{slug}")
    public WebsitePageView page(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebPage p = pages.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Page"));
        i18n.apply(WebsiteTranslatable.PAGE, WebsiteLang.of(lang), p, WebPage::getId, WebsiteTranslations.PAGE);
        return new WebsitePageView(p.getSlug(), p.getTitle(), p.getBody());
    }
}
