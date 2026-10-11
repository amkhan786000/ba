package com.rahbar.website;

import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Section;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin > Website > Blog and Success stories: written by many, published by few.
 * <ul>
 * <li>Blog: WEBSITE_BLOG VIEW writes posts and sees one's own drafts; EDIT sees and edits every post.</li>
 * <li>Stories: WEBSITE_STORIES; chapter / RCC-scoped users only their own chapter's / center's stories, which are
 * tied to it automatically (an RCC story also to the Rahbar Coaching Centres project). Consent is required.</li>
 * <li>Only WEBSITE_BLOG_PUBLISH / WEBSITE_STORIES_PUBLISH may publish; others tick "ready for review" and the
 * publishers are notified. Once published, only a publisher may change or delete it.</li>
 * </ul>
 */
final class WebsiteAdminPostsController {

    private WebsiteAdminPostsController() {
    }

    /** Rahbar Coaching Centres: the project every RCC story belongs to. */
    static final String COACHING_PROJECT = "rahbar-coaching-centres";

    private static Map<Long, String> names(UserRepository users, Collection<? extends com.rahbar.entity.Modifiable> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> {
            if (r.getCreatedBy() != null) ids.add(r.getCreatedBy());
            if (r.getUpdatedBy() != null) ids.add(r.getUpdatedBy());
        });
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) users.findAllById(ids).forEach(u -> out.put(u.getId(), u.getName()));
        return out;
    }

    @RestController
    @RequestMapping("/api/admin/website/blog")
    static class Blog {
        private final WebBlogPostRepository posts;
        private final WebProjectRepository projects;
        private final UserRepository users;
        private final WebsiteNotifier notifier;

        Blog(WebBlogPostRepository posts, WebProjectRepository projects, UserRepository users, WebsiteNotifier notifier) {
            this.posts = posts;
            this.projects = projects;
            this.users = users;
            this.notifier = notifier;
        }

        static boolean canPublish() {
            return WebsiteSupport.can(Section.WEBSITE_BLOG_PUBLISH, Section.Level.EDIT);
        }

        /** Editors and publishers see every post; writers only their own. */
        static boolean seesAll() {
            return canPublish() || WebsiteSupport.can(Section.WEBSITE_BLOG, Section.Level.EDIT);
        }

        @GetMapping
        @Transactional(readOnly = true)
        public Map<String, Object> list() {
            WebsiteSupport.require(Section.WEBSITE_BLOG, Section.Level.VIEW);
            List<WebBlogPost> rows = seesAll() ? posts.findAll(Sort.by(Sort.Direction.DESC, "id"))
                    : posts.findByCreatedByOrderByIdDesc(WebsiteSupport.me().getId());
            return Map.of("data", rows, "names", names(users, rows));
        }

        @GetMapping("/{id}")
        @Transactional(readOnly = true)
        public WebBlogPost get(@PathVariable Long id) {
            WebsiteSupport.require(Section.WEBSITE_BLOG, Section.Level.VIEW);
            return visible(id);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Transactional
        public WebBlogPost create(@RequestBody WebBlogPost body) {
            WebsiteSupport.require(Section.WEBSITE_BLOG, Section.Level.VIEW);
            WebBlogPost p = new WebBlogPost();
            p.setSource(WebBlogPost.Source.STAFF);
            copy(body, p);
            WebBlogPost saved = posts.save(p);
            askForReview(saved, false);
            return saved;
        }

        @PutMapping("/{id}")
        @Transactional
        public WebBlogPost update(@PathVariable Long id, @RequestBody WebBlogPost body) {
            WebsiteSupport.require(Section.WEBSITE_BLOG, Section.Level.VIEW);
            WebBlogPost p = visible(id);
            checkMayChange(p);
            boolean wasWaiting = p.isReviewRequested();
            copy(body, p);
            WebBlogPost saved = posts.save(p);
            askForReview(saved, wasWaiting);
            return saved;
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Transactional
        public void delete(@PathVariable Long id) {
            WebsiteSupport.require(Section.WEBSITE_BLOG, Section.Level.VIEW);
            WebBlogPost p = visible(id);
            checkMayChange(p);
            posts.delete(p);
        }

        private void copy(WebBlogPost body, WebBlogPost p) {
            p.setTitle(WebsiteSupport.text(body.getTitle(), "Title", 200, true));
            p.setSlug(WebsiteSupport.slug(body.getSlug()));
            p.setTopic(WebsiteSupport.text(body.getTopic(), "Topic", 80, true));
            p.setAuthor(WebsiteSupport.text(body.getAuthor(), "Author", 120, false));
            p.setSummary(WebsiteSupport.text(body.getSummary(), "Summary", 600, false));
            p.setContent(WebsiteSupport.text(body.getContent(), "Content", 1_000_000, true));
            p.setCoverImageUrl(WebsiteSupport.text(body.getCoverImageUrl(), "Cover image", 255, false));
            if (body.getProjectId() != null && !projects.existsById(body.getProjectId())) throw WebsiteSupport.bad("Unknown project.");
            p.setProjectId(body.getProjectId());
            p.setReviewRequested(body.isReviewRequested());
            if (canPublish()) {
                p.setPublished(body.isPublished());
                p.setPublishedAt(body.getPublishedAt());
                if (p.isPublished()) p.setReviewRequested(false);
            }
        }

        private void checkMayChange(WebBlogPost p) {
            if (seesAll()) return;
            if (p.isPublished()) throw com.rahbar.security.Access.forbidden("This post is published; only a publisher can change it now.");
        }

        private void askForReview(WebBlogPost p, boolean wasWaiting) {
            if (p.isReviewRequested() && !wasWaiting && !p.isPublished()) {
                notifier.notify(Section.WEBSITE_BLOG_PUBLISH, null, null, "Blog post to review: " + p.getTitle(),
                        WebsiteSupport.me().getName() + " asked for a review.", "/admin/website/blog");
            }
        }

        private WebBlogPost visible(Long id) {
            return posts.findById(id)
                    .filter(p -> seesAll() || Objects.equals(p.getCreatedBy(), WebsiteSupport.me().getId()))
                    .orElseThrow(() -> WebsiteSupport.notFound("Blog post"));
        }
    }

    @RestController
    @RequestMapping("/api/admin/website/stories")
    static class Stories {
        private final WebStoryRepository stories;
        private final WebProjectRepository projects;
        private final ChapterRepository chapters;
        private final RccCenterRepository centers;
        private final WebTranslationRepository translations;
        private final UserRepository users;
        private final WebsiteNotifier notifier;

        Stories(WebStoryRepository stories, WebProjectRepository projects, ChapterRepository chapters, RccCenterRepository centers,
                WebTranslationRepository translations, UserRepository users, WebsiteNotifier notifier) {
            this.stories = stories;
            this.projects = projects;
            this.chapters = chapters;
            this.centers = centers;
            this.translations = translations;
            this.users = users;
            this.notifier = notifier;
        }

        static boolean canPublish() {
            return WebsiteSupport.can(Section.WEBSITE_STORIES_PUBLISH, Section.Level.EDIT);
        }

        static boolean inScope(WebStory s) {
            Long chapter = WebsiteSupport.scopedChapter();
            Long center = WebsiteSupport.scopedCenter();
            return (chapter == null || Objects.equals(s.getChapterId(), chapter))
                    && (center == null || Objects.equals(s.getRccCenterId(), center));
        }

        @GetMapping
        @Transactional(readOnly = true)
        public Map<String, Object> list() {
            WebsiteSupport.require(Section.WEBSITE_STORIES, Section.Level.VIEW);
            List<WebStory> rows = stories.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().filter(Stories::inScope).toList();
            return Map.of("data", rows, "names", names(users, rows));
        }

        @GetMapping("/{id}")
        @Transactional(readOnly = true)
        public WebStory get(@PathVariable Long id) {
            WebsiteSupport.require(Section.WEBSITE_STORIES, Section.Level.VIEW);
            return find(id);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Transactional
        public WebStory create(@RequestBody WebStory body) {
            WebsiteSupport.require(Section.WEBSITE_STORIES, Section.Level.EDIT);
            WebStory s = new WebStory();
            copy(body, s);
            WebStory saved = stories.save(s);
            askForReview(saved, false);
            return saved;
        }

        @PutMapping("/{id}")
        @Transactional
        public WebStory update(@PathVariable Long id, @RequestBody WebStory body) {
            WebsiteSupport.require(Section.WEBSITE_STORIES, Section.Level.EDIT);
            WebStory s = find(id);
            if (!canPublish() && s.isPublished()) {
                throw com.rahbar.security.Access.forbidden("This story is published; only a publisher can change it now.");
            }
            boolean wasWaiting = s.isReviewRequested();
            copy(body, s);
            WebStory saved = stories.save(s);
            askForReview(saved, wasWaiting);
            return saved;
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Transactional
        public void delete(@PathVariable Long id) {
            WebsiteSupport.require(Section.WEBSITE_STORIES, Section.Level.EDIT);
            WebStory s = find(id);
            if (!canPublish() && s.isPublished()) {
                throw com.rahbar.security.Access.forbidden("This story is published; only a publisher can delete it.");
            }
            translations.deleteAllFor(WebsiteTranslatable.STORY.name(), s.getId());
            stories.delete(s);
        }

        private void copy(WebStory body, WebStory s) {
            s.setTitle(WebsiteSupport.text(body.getTitle(), "Title", 200, true));
            s.setSlug(WebsiteSupport.slug(body.getSlug()));
            s.setPersonName(WebsiteSupport.text(body.getPersonName(), "Name of the person", 120, true));
            if (!body.isConsentGiven()) {
                throw WebsiteSupport.bad("Please confirm that the person (or their parent / guardian) agreed to share this story.");
            }
            s.setConsentGiven(true);
            s.setShowFullName(body.isShowFullName());
            s.setShowPhoto(body.isShowPhoto());
            s.setPhotoUrl(WebsiteSupport.text(body.getPhotoUrl(), "Photo", 255, false));
            s.setSummary(WebsiteSupport.text(body.getSummary(), "Summary", 600, false));
            s.setStory(WebsiteSupport.text(body.getStory(), "The story", 1_000_000, true));
            s.setNowWhere(WebsiteSupport.text(body.getNowWhere(), "Where are they now", 200, false));
            if (body.getYear() != null && (body.getYear() < 1990 || body.getYear() > 2100)) throw WebsiteSupport.bad("Year must be between 1990 and 2100.");
            s.setYear(body.getYear());
            if (body.getProjectId() != null && !projects.existsById(body.getProjectId())) throw WebsiteSupport.bad("Unknown project.");
            s.setProjectId(body.getProjectId());
            // a lead's stories always belong to their own chapter / center (whatever the form sent)
            Long chapter = WebsiteSupport.scopedChapter();
            if (chapter != null) {
                s.setChapterId(chapter);
            } else {
                if (body.getChapterId() != null && !chapters.existsById(body.getChapterId())) throw WebsiteSupport.bad("Unknown chapter.");
                s.setChapterId(body.getChapterId());
            }
            Long center = WebsiteSupport.scopedCenter();
            if (center != null) {
                s.setRccCenterId(center);
                projects.findBySlugAndActiveTrue(COACHING_PROJECT).ifPresent(p -> s.setProjectId(p.getId()));
            } else {
                if (body.getRccCenterId() != null && !centers.existsById(body.getRccCenterId())) throw WebsiteSupport.bad("Unknown RCC center.");
                s.setRccCenterId(body.getRccCenterId());
            }
            s.setReviewRequested(body.isReviewRequested());
            if (canPublish()) {
                s.setPublished(body.isPublished());
                s.setPublishedAt(body.getPublishedAt());
                if (s.isPublished()) s.setReviewRequested(false);
            }
        }

        private void askForReview(WebStory s, boolean wasWaiting) {
            if (s.isReviewRequested() && !wasWaiting && !s.isPublished()) {
                notifier.notify(Section.WEBSITE_STORIES_PUBLISH, s.getChapterId(), s.getRccCenterId(), "Success story to review: " + s.getTitle(),
                        WebsiteSupport.me().getName() + " asked for a review.", "/admin/website/stories");
            }
        }

        private WebStory find(Long id) {
            return stories.findById(id).filter(Stories::inScope).orElseThrow(() -> WebsiteSupport.notFound("Success story"));
        }
    }
}
