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
import com.rahbar.service.PublicDirectoryService.PublicCenter;
import com.rahbar.service.PublicDirectoryService.PublicChapter;
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

    public record ProjectView(Long id, String name, String slug, String category, String summary, String description,
                              String sponsorshipInfo, String imageUrl, String websiteUrl, String galleryAlbum,
                              int displayOrder, boolean featured, boolean active) {
        static ProjectView of(WebProject p) {
            return new ProjectView(p.getId(), p.getName(), p.getSlug(), p.getCategory(), p.getSummary(), p.getDescription(),
                    p.getSponsorshipInfo(), p.getImageUrl(), p.getWebsiteUrl(), p.getGalleryAlbum(), p.getDisplayOrder(),
                    p.isFeatured(), p.isActive());
        }
    }

    public record NewsView(Long id, String title, String slug, WebNews.Kind kind, String summary, String content,
                           LocalDate eventDate, String location, Long chapterId, String imageUrl, boolean published,
                           LocalDateTime createdAt) {
        static NewsView of(WebNews n) {
            return new NewsView(n.getId(), n.getTitle(), n.getSlug(), n.getKind(), n.getSummary(), n.getContent(),
                    n.getEventDate(), n.getLocation(), n.getChapterId(), n.getImageUrl(), n.isPublished(), n.getCreatedAt());
        }
    }

    /** A Knowledge Hub section as the website shows it: name and address, plus how many posts it has. */
    public record CategoryView(Long id, String slug, String name, String description, long posts) {
    }

    /** The author's email is never shown. */
    public record PostView(Long id, String title, String slug, String topic, String author, String summary, String content,
                           String coverImageUrl, Long projectId, Long categoryId, CategoryRef category,
                           String attachmentUrl, String attachmentName, String mediaUrl, String subjectName,
                           String subjectDetails, boolean published, LocalDateTime publishedAt, LocalDateTime createdAt) {
        static PostView of(WebBlogPost p) {
            return of(p, Map.of());
        }

        static PostView of(WebBlogPost p, Map<Long, CategoryRef> categories) {
            return new PostView(p.getId(), p.getTitle(), p.getSlug(), p.getTopic(), p.getAuthor(), p.getSummary(),
                    p.getContent(), p.getCoverImageUrl(), p.getProjectId(), p.getCategoryId(),
                    p.getCategoryId() == null ? null : categories.get(p.getCategoryId()), // none after a category was deleted
                    p.getAttachmentUrl(), p.getAttachmentName(), p.getMediaUrl(), p.getSubjectName(), p.getSubjectDetails(),
                    p.isPublished(), p.getPublishedAt(), p.getCreatedAt());
        }
    }

    public record CategoryRef(String slug, String name) {
    }

    public record ImageView(Long id, String title, String caption, String album, LocalDate takenOn, int displayOrder,
                            boolean published, String url, String contentType, long sizeBytes, LocalDateTime createdAt) {
        static ImageView of(WebGalleryImage g) {
            return new ImageView(g.getId(), g.getTitle(), g.getCaption(), g.getAlbum(), g.getTakenOn(), g.getDisplayOrder(),
                    g.isPublished(), g.getUrl(), g.getContentType(), g.getSizeBytes(), g.getCreatedAt());
        }
    }

    /** Privacy settings applied: first name and initial unless the full name may be shown; the photo only if allowed. */
    public record StoryView(Long id, String slug, String title, String name, String photoUrl, String summary, String story,
                            String nowWhere, Integer year, Long projectId, Long chapterId, Long coachingCenterId,
                            LocalDateTime publishedAt) {
        static StoryView of(WebStory s) {
            return new StoryView(s.getId(), s.getSlug(), s.getTitle(), s.publicName(), s.isShowPhoto() ? s.getPhotoUrl() : null,
                    s.getSummary(), s.getStory(), s.getNowWhere(), s.getYear(), s.getProjectId(), s.getChapterId(),
                    s.getRccCenterId(), s.getPublishedAt());
        }
    }

    /** Contact details only when the member allowed it. */
    public record DirectoryEntry(Long id, String fullName, WebMember.HomeState homeState, String homeDistrict,
                                 String currentCountry, String currentCity, String profession, String organization,
                                 String email, String phone) {
        static DirectoryEntry of(WebMember m) {
            return new DirectoryEntry(m.getId(), m.getFullName(), m.getHomeState(), m.getHomeDistrict(), m.getCurrentCountry(),
                    m.getCurrentCity(), m.getProfession(), m.getOrganization(),
                    m.isShowContact() ? m.getEmail() : null, m.isShowContact() ? m.getPhone() : null);
        }
    }

    /** rebGirls: girls studying at Rahbar-e-Banaat now (status ACTIVE in REB > Girls). */
    public record Stats(long chapters, long projects, long coachingCenters, Long students, long members, long rebGirls) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.RebStudentRepository rebStudents;

    public record MenuItem(String slug, String title) {
    }

    public record PageView(String slug, String title, String body) {
    }

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

    /** A way to help on the volunteer form, with its lists split into items (in the visitor's language). */
    public record VolunteerOptionView(String key, String kind, String title, String description,
                                      String choicesLabel, List<String> choices, String extraLabel, List<String> extraChoices) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private WebVolunteerOptionRepository volunteerOptions;

    /** The active ways to help on "Volunteer with us", in their order (Admin > Volunteers > Form options). */
    @GetMapping("/volunteer-options")
    public List<VolunteerOptionView> volunteerOptions(@RequestParam(required = false) String lang) {
        return i18n.apply(WebsiteTranslatable.VOLUNTEER_OPTION, WebsiteLang.of(lang),
                        volunteerOptions.findByActiveTrueOrderByDisplayOrderAscIdAsc(), WebVolunteerOption::getId, WebsiteTranslations.VOLUNTEER_OPTION)
                .stream()
                .map(o -> new VolunteerOptionView(o.getOptionKey(), o.getKind().name(), o.getTitle(), o.getDescription(),
                        o.getChoicesLabel(), WebVolunteerOption.lines(o.getChoices()), o.getExtraLabel(), WebVolunteerOption.lines(o.getExtraChoices())))
                .toList();
    }

    @GetMapping("/coaching-centers")
    public List<PublicCenter> coachingCenters() {
        return directory.centers();
    }

    @GetMapping("/stats")
    public Stats stats() {
        return new Stats(directory.chapters().size(), projects.countByActiveTrue(), directory.centers().size(),
                directory.stats().students(), members.countByApprovedTrue(), rebStudents.countByStatus("ACTIVE"));
    }

    // ---- projects and news ---------------------------------------------------------------------------------

    @GetMapping("/projects")
    public List<ProjectView> projects(@RequestParam(required = false) String lang) {
        return i18n.apply(WebsiteTranslatable.PROJECT, WebsiteLang.of(lang), projects.findByActiveTrueOrderByDisplayOrderAscNameAsc(),
                WebProject::getId, WebsiteTranslations.PROJECT).stream().map(ProjectView::of).toList();
    }

    @GetMapping("/projects/{slug}")
    public ProjectView project(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebProject p = projects.findBySlugAndActiveTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Project"));
        return ProjectView.of(i18n.apply(WebsiteTranslatable.PROJECT, WebsiteLang.of(lang), p, WebProject::getId, WebsiteTranslations.PROJECT));
    }

    @GetMapping("/news")
    public PageBody<NewsView> news(@RequestParam(required = false) WebNews.Kind kind,
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
        return PageBody.of(result.map(NewsView::of));
    }

    @GetMapping("/news/{slug}")
    public NewsView newsItem(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebNews n = news.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("News item"));
        return NewsView.of(i18n.apply(WebsiteTranslatable.NEWS, WebsiteLang.of(lang), n, WebNews::getId, WebsiteTranslations.NEWS));
    }

    // ---- blog ----------------------------------------------------------------------------------------------

    /** Active categories (Knowledge Hub sections), in their order, with the number of published posts in each. */
    @GetMapping("/blog/categories")
    public List<CategoryView> blogCategories(@RequestParam(required = false) String lang) {
        List<WebBlogCategory> list = i18n.apply(WebsiteTranslatable.CATEGORY, WebsiteLang.of(lang),
                categories.findByActiveTrueOrderByDisplayOrderAscNameAsc(), WebBlogCategory::getId, WebsiteTranslations.CATEGORY);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : posts.countPublishedByCategory()) counts.put((Long) row[0], (Long) row[1]);
        return list.stream().map(c -> new CategoryView(c.getId(), c.getSlug(), c.getName(), c.getDescription(),
                counts.getOrDefault(c.getId(), 0L))).toList();
    }

    /** slug -> name of the active categories (names in the visitor's language). */
    private Map<Long, CategoryRef> categoryRefs(String lang) {
        Map<Long, CategoryRef> out = new HashMap<>();
        i18n.apply(WebsiteTranslatable.CATEGORY, WebsiteLang.of(lang), categories.findByActiveTrueOrderByDisplayOrderAscNameAsc(),
                WebBlogCategory::getId, WebsiteTranslations.CATEGORY).forEach(c -> out.put(c.getId(), new CategoryRef(c.getSlug(), c.getName())));
        return out;
    }

    /** projectId 0 = posts not linked to a project ("Others"); category = a category's slug (Knowledge Hub section). */
    @GetMapping("/blog")
    public PageBody<PostView> blog(@RequestParam(required = false) String q,
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
        Map<Long, CategoryRef> refs = categoryRefs(lang);
        return PageBody.of(posts.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), order)).map(p -> PostView.of(p, refs)));
    }

    @GetMapping("/blog/topics")
    public List<String> blogTopics() {
        return posts.publishedTopics();
    }

    @GetMapping("/blog/{slug}")
    public PostView post(@PathVariable String slug, @RequestParam(required = false) String lang) {
        return PostView.of(findPost(slug), categoryRefs(lang));
    }

    /** Other posts on the same topic, or else in the same category. */
    @GetMapping("/blog/{slug}/related")
    public List<PostView> related(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebBlogPost p = findPost(slug);
        Map<Long, CategoryRef> refs = categoryRefs(lang);
        List<WebBlogPost> list = posts.findTop3ByPublishedTrueAndTopicAndIdNotOrderByPublishedAtDesc(p.getTopic(), p.getId());
        if (list.isEmpty() && p.getCategoryId() != null) {
            list = posts.findTop3ByPublishedTrueAndCategoryIdAndIdNotOrderByPublishedAtDesc(p.getCategoryId(), p.getId());
        }
        return list.stream().map(x -> PostView.of(x, refs)).toList();
    }

    private WebBlogPost findPost(String slug) {
        return posts.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Post"));
    }

    // ---- success stories -----------------------------------------------------------------------------------

    /** projectId 0 = stories not linked to a project ("Others"). */
    @GetMapping("/stories")
    public PageBody<StoryView> stories(@RequestParam(required = false) String q,
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
        return PageBody.of(result.map(StoryView::of));
    }

    @GetMapping("/stories/years")
    public List<Integer> storyYears() {
        return stories.publishedYears();
    }

    @GetMapping("/stories/{slug}")
    public StoryView story(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebStory s = stories.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Success story"));
        return StoryView.of(i18n.apply(WebsiteTranslatable.STORY, WebsiteLang.of(lang), s, WebStory::getId, WebsiteTranslations.STORY));
    }

    // ---- gallery and image files ---------------------------------------------------------------------------

    @GetMapping("/gallery")
    public PageBody<ImageView> gallery(@RequestParam(required = false) String album,
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
        return PageBody.of(images.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), GALLERY_ORDER)).map(ImageView::of));
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
    public PageBody<DirectoryEntry> directory(@RequestParam(required = false) String q,
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
        return PageBody.of(members.findAll(spec, pageable).map(DirectoryEntry::of));
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
    public List<MenuItem> menu(@RequestParam(required = false) String lang) {
        return i18n.apply(WebsiteTranslatable.PAGE, WebsiteLang.of(lang), pages.findByPublishedTrueAndShowInMenuTrueOrderByMenuOrderAscTitleAsc(),
                WebPage::getId, WebsiteTranslations.PAGE).stream().map(p -> new MenuItem(p.getSlug(), p.getTitle())).toList();
    }

    @GetMapping("/pages/{slug}")
    public PageView page(@PathVariable String slug, @RequestParam(required = false) String lang) {
        WebPage p = pages.findBySlugAndPublishedTrue(slug).orElseThrow(() -> WebsiteSupport.notFound("Page"));
        i18n.apply(WebsiteTranslatable.PAGE, WebsiteLang.of(lang), p, WebPage::getId, WebsiteTranslations.PAGE);
        return new PageView(p.getSlug(), p.getTitle(), p.getBody());
    }
}
