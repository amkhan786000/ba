package com.rahbar.website;

import com.rahbar.repository.ChapterRepository;
import com.rahbar.security.Section;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Admin > Website: projects, news & events, directory, messages and custom pages (see WebsiteCrud). */
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
