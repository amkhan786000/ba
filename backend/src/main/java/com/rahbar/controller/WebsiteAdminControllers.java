package com.rahbar.controller;

import com.rahbar.entity.WebBlogCategory;
import com.rahbar.repository.WebBlogCategoryRepository;
import com.rahbar.entity.WebMember;
import com.rahbar.repository.WebMemberRepository;
import com.rahbar.entity.WebMessage;
import com.rahbar.repository.WebMessageRepository;
import com.rahbar.entity.WebNews;
import com.rahbar.repository.WebNewsRepository;
import com.rahbar.entity.WebPage;
import com.rahbar.repository.WebPageRepository;
import com.rahbar.entity.WebProject;
import com.rahbar.repository.WebProjectRepository;
import com.rahbar.entity.WebVolunteerOption;
import com.rahbar.repository.WebVolunteerOptionRepository;
import com.rahbar.controller.WebsiteAdminControllers;
import com.rahbar.controller.WebsiteCrud;
import com.rahbar.util.WebsiteSupport;
import com.rahbar.entity.WebsiteTranslatable;

import com.rahbar.repository.ChapterRepository;
import com.rahbar.security.Section;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Admin > Website: projects, news & events, directory, messages, custom pages and blog categories (see WebsiteCrud). */
final class WebsiteAdminControllers {

    private WebsiteAdminControllers() {
    }

    @RestController
    @RequestMapping("/api/admin/website/projects")
    static class Projects extends WebsiteCrud<WebProject> {
        Projects(WebProjectRepository r) {
            super(r, Section.WEBSITE_PROJECTS, "Project", WebProject::getId);
        }

        @Override
        protected void validate(WebProject p) {
            p.setName(WebsiteSupport.text(p.getName(), "Name", 255, true));
            p.setSlug(WebsiteSupport.slug(p.getSlug()));
            p.setSummary(WebsiteSupport.text(p.getSummary(), "Summary", 600, true));
            p.setGalleryAlbum(WebsiteSupport.text(p.getGalleryAlbum(), "Photo album", 80, false));
        }

        @Override
        protected WebsiteTranslatable type() {
            return WebsiteTranslatable.PROJECT;
        }

        @Override
        protected Map<String, Function<WebProject, String>> revisionFields() {
            return Map.of("summary", WebProject::getSummary, "description", WebProject::getDescription);
        }
    }

    /** Chapter-scoped users manage only their chapter's news and events. */
    @RestController
    @RequestMapping("/api/admin/website/news")
    static class News extends WebsiteCrud<WebNews> {
        private final ChapterRepository chapters;

        News(WebNewsRepository r, ChapterRepository chapters) {
            super(r, Section.WEBSITE_NEWS, "News item", WebNews::getId);
            this.chapters = chapters;
        }

        @Override
        protected void validate(WebNews n) {
            n.setTitle(WebsiteSupport.text(n.getTitle(), "Title", 255, true));
            n.setSlug(WebsiteSupport.slug(n.getSlug()));
            if (n.getKind() == null) n.setKind(WebNews.Kind.NEWS);
            n.setSummary(WebsiteSupport.text(n.getSummary(), "Summary", 600, false));
            if (n.getChapterId() != null && !chapters.existsById(n.getChapterId())) throw WebsiteSupport.bad("Unknown chapter.");
        }

        @Override
        protected boolean inScope(WebNews n) {
            Long mine = WebsiteSupport.scopedChapter();
            return mine == null || Objects.equals(n.getChapterId(), mine);
        }

        @Override
        protected void applyScope(WebNews n) {
            Long mine = WebsiteSupport.scopedChapter();
            if (mine != null) n.setChapterId(mine);
        }

        @Override
        protected WebsiteTranslatable type() {
            return WebsiteTranslatable.NEWS;
        }

        @Override
        protected Map<String, Function<WebNews, String>> revisionFields() {
            return Map.of("content", WebNews::getContent);
        }
    }

    /** The worldwide directory; chapter-scoped users manage only their chapter's members. */
    @RestController
    @RequestMapping("/api/admin/website/directory")
    static class Directory extends WebsiteCrud<WebMember> {
        private final ChapterRepository chapters;

        Directory(WebMemberRepository r, ChapterRepository chapters) {
            super(r, Section.WEBSITE_DIRECTORY, "Directory member", WebMember::getId);
            this.chapters = chapters;
        }

        @Override
        protected void validate(WebMember m) {
            m.setFullName(WebsiteSupport.text(m.getFullName(), "Full name", 255, true));
            if (m.getHomeState() == null) throw WebsiteSupport.bad("Home state is required.");
            m.setCurrentCountry(WebsiteSupport.text(m.getCurrentCountry(), "Country", 255, true));
            m.setEmail(WebsiteSupport.email(m.getEmail(), "Email", true));
            if (m.getChapterId() != null && !chapters.existsById(m.getChapterId())) throw WebsiteSupport.bad("Unknown chapter.");
        }

        @Override
        protected boolean inScope(WebMember m) {
            Long mine = WebsiteSupport.scopedChapter();
            return mine == null || Objects.equals(m.getChapterId(), mine);
        }

        @Override
        protected void applyScope(WebMember m) {
            Long mine = WebsiteSupport.scopedChapter();
            if (mine != null) m.setChapterId(mine);
        }
    }

    /** Contact-form messages: staff read them and mark them handled. */
    @RestController
    @RequestMapping("/api/admin/website/messages")
    static class Messages extends WebsiteCrud<WebMessage> {
        Messages(WebMessageRepository r) {
            super(r, Section.WEBSITE_MESSAGES, "Message", WebMessage::getId);
        }

        @Override
        protected boolean creatable() {
            return false;
        }

        @Override
        protected void validate(WebMessage m) {
            m.setName(WebsiteSupport.text(m.getName(), "Name", 120, true));
            m.setEmail(WebsiteSupport.email(m.getEmail(), "Email", true));
            m.setSubject(WebsiteSupport.text(m.getSubject(), "Subject", 200, true));
            m.setMessage(WebsiteSupport.text(m.getMessage(), "Message", 5000, true));
            if (m.getTopic() == null) m.setTopic(WebMessage.Topic.GENERAL);
        }
    }

    /** Knowledge Hub sections every blog post belongs to; WEBSITE_BLOG_CATEGORIES (set on Roles & permissions). */
    @RestController
    @RequestMapping("/api/admin/website/categories")
    static class Categories extends WebsiteCrud<WebBlogCategory> {
        static final java.util.Set<String> RESERVED_SLUGS = java.util.Set.of("all", "write");

        Categories(WebBlogCategoryRepository r) {
            super(r, Section.WEBSITE_BLOG_CATEGORIES, "Category", WebBlogCategory::getId);
        }

        @Override
        protected void validate(WebBlogCategory c) {
            c.setName(WebsiteSupport.text(c.getName(), "Name", 100, true));
            c.setSlug(WebsiteSupport.slug(c.getSlug()));
            if (c.getSlug().length() > 100) throw WebsiteSupport.bad("Slug is too long (at most 100 characters).");
            // the website uses /knowledge-hub/all and /knowledge-hub/write itself
            if (RESERVED_SLUGS.contains(c.getSlug())) throw WebsiteSupport.bad("The slug \"" + c.getSlug() + "\" is used by the website; choose another.");
            c.setDescription(WebsiteSupport.text(c.getDescription(), "Description", 600, false));
        }

        @Override
        protected WebsiteTranslatable type() {
            return WebsiteTranslatable.CATEGORY;
        }

        /** As on the website. */
        @Override
        protected org.springframework.data.domain.Sort listOrder() {
            return org.springframework.data.domain.Sort.by("displayOrder", "name");
        }
    }

    /**
     * The ways to help on the website's volunteer form (Admin > Volunteers > Form options). There is at most one
     * option of each built-in kind (mentor, tutor, sponsor, chapter); any number of OTHER ones.
     */
    @RestController
    @RequestMapping("/api/admin/website/volunteer-options")
    static class VolunteerOptions extends WebsiteCrud<WebVolunteerOption> {
        private final WebVolunteerOptionRepository options;

        VolunteerOptions(WebVolunteerOptionRepository r) {
            super(r, Section.VOLUNTEER_OPTIONS, "Volunteer option", WebVolunteerOption::getId);
            this.options = r;
        }

        @Override
        protected void validate(WebVolunteerOption o) {
            o.setTitle(WebsiteSupport.text(o.getTitle(), "Title", 120, true));
            if (o.getOptionKey() == null || o.getOptionKey().isBlank()) o.setOptionKey(WebsiteSupport.slugify(o.getTitle()));
            o.setOptionKey(WebsiteSupport.slug(o.getOptionKey()));
            if (o.getOptionKey().length() > 60) throw WebsiteSupport.bad("Key is too long (at most 60 characters).");
            options.findByOptionKey(o.getOptionKey()).filter(other -> !other.getId().equals(o.getId()))
                    .ifPresent(other -> { throw WebsiteSupport.bad("Another option already uses the key \"" + o.getOptionKey() + "\"."); });
            if (o.getKind() == null) o.setKind(WebVolunteerOption.Kind.OTHER);
            if (o.getKind() != WebVolunteerOption.Kind.OTHER
                    && (o.getId() == null ? options.existsByKind(o.getKind()) : options.existsByKindAndIdNot(o.getKind(), o.getId()))) {
                throw WebsiteSupport.bad("There is already a " + o.getKind().name().toLowerCase(java.util.Locale.ROOT)
                        + " option; edit that one, or choose the kind OTHER.");
            }
            o.setDescription(WebsiteSupport.text(o.getDescription(), "Description", 400, false));
            o.setChoicesLabel(WebsiteSupport.text(o.getChoicesLabel(), "First list heading", 120, false));
            o.setChoices(lines(o.getChoices(), "First list"));
            o.setExtraLabel(WebsiteSupport.text(o.getExtraLabel(), "Second list heading", 120, false));
            o.setExtraChoices(lines(o.getExtraChoices(), "Second list"));
        }

        /** Tidies a list: one item per line, no blanks or repeats; each item at most 100 characters. */
        private static String lines(String text, String label) {
            java.util.List<String> items = WebVolunteerOption.lines(text);
            for (String item : items) {
                if (item.length() > 100) throw WebsiteSupport.bad(label + ": \"" + item.substring(0, 30) + "…\" is too long (at most 100 characters).");
                if (item.contains(",")) throw WebsiteSupport.bad(label + ": items can't contain a comma (\"" + item + "\").");
            }
            return items.isEmpty() ? null : String.join("\n", items);
        }

        @Override
        protected WebsiteTranslatable type() {
            return WebsiteTranslatable.VOLUNTEER_OPTION;
        }

        /** As on the website. */
        @Override
        protected org.springframework.data.domain.Sort listOrder() {
            return org.springframework.data.domain.Sort.by("displayOrder", "id");
        }
    }

    /** Custom pages of the website (shown at /p/<slug>, optionally in its menu). */
    @RestController
    @RequestMapping("/api/admin/website/pages")
    static class Pages extends WebsiteCrud<WebPage> {
        Pages(WebPageRepository r) {
            super(r, Section.WEBSITE_PAGES, "Page", WebPage::getId);
        }

        @Override
        protected void validate(WebPage p) {
            p.setTitle(WebsiteSupport.text(p.getTitle(), "Title", 200, true));
            p.setSlug(WebsiteSupport.slug(p.getSlug()));
        }

        @Override
        protected WebsiteTranslatable type() {
            return WebsiteTranslatable.PAGE;
        }

        @Override
        protected Map<String, Function<WebPage, String>> revisionFields() {
            return Map.of("title", WebPage::getTitle, "body", WebPage::getBody);
        }
    }
}
