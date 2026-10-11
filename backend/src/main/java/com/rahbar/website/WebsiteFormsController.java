package com.rahbar.website;

import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.security.Section;
import com.rahbar.service.VolunteerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * The website's public forms: contact, directory sign-up, "Write for us" (a blog post sent for review) and
 * "Volunteer with us". Each has a hidden bot-trap field ("website") and its own limit per visitor; staff are notified.
 */
@RestController
@RequestMapping("/api/public/website")
public class WebsiteFormsController {

    static final long MAX_PHOTO = 5L * 1024 * 1024;

    private final WebMessageRepository messages;
    private final WebMemberRepository members;
    private final WebBlogPostRepository posts;
    private final WebProjectRepository projects;
    private final WebGalleryImageRepository images;
    private final ChapterRepository chapters;
    private final WebsiteImageStorage storage;
    private final WebsiteNotifier notifier;
    private final VolunteerService volunteers;
    private final WebBlogCategoryRepository categories;

    private final WebsiteLimiter contactLimit = new WebsiteLimiter(5, 300);
    private final WebsiteLimiter directoryLimit = new WebsiteLimiter(3, 200);
    private final WebsiteLimiter blogLimit = new WebsiteLimiter(3, 100);
    private final WebsiteLimiter volunteerLimit = new WebsiteLimiter(3, 200);

    public WebsiteFormsController(WebMessageRepository messages, WebMemberRepository members, WebBlogPostRepository posts,
                                  WebProjectRepository projects, WebGalleryImageRepository images, ChapterRepository chapters,
                                  WebsiteImageStorage storage, WebsiteNotifier notifier, VolunteerService volunteers,
                                  WebBlogCategoryRepository categories) {
        this.messages = messages;
        this.members = members;
        this.posts = posts;
        this.projects = projects;
        this.images = images;
        this.chapters = chapters;
        this.storage = storage;
        this.notifier = notifier;
        this.volunteers = volunteers;
        this.categories = categories;
    }

    private static void limit(WebsiteLimiter limiter, HttpServletRequest request, String message) {
        if (!limiter.allow(WebsiteSupport.clientIp(request))) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    private static boolean bot(String trap) {
        return trap != null && !trap.isBlank();
    }

    // ---- contact -------------------------------------------------------------------------------------------

    public record ContactRequest(String name, String email, String phone, WebMessage.Topic topic, String subject,
                                 String message, String website) {
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, String> contact(@RequestBody ContactRequest r, HttpServletRequest request) {
        String thanks = "Thank you for reaching out. We will get back to you soon.";
        if (bot(r.website())) return Map.of("message", thanks);
        WebMessage m = new WebMessage();
        m.setName(WebsiteSupport.text(r.name(), "Name", 120, true));
        m.setEmail(WebsiteSupport.email(r.email(), "Email", true));
        m.setPhone(WebsiteSupport.text(r.phone(), "Phone", 40, false));
        m.setTopic(r.topic() == null ? WebMessage.Topic.GENERAL : r.topic());
        m.setSubject(WebsiteSupport.text(r.subject(), "Subject", 200, true));
        m.setMessage(WebsiteSupport.text(r.message(), "Message", 5000, true));
        limit(contactLimit, request, "You have sent several messages already. Please try again in an hour.");
        messages.save(m);
        notifier.notify(Section.WEBSITE_MESSAGES, null, null, "Website message: " + m.getSubject(),
                m.getName() + " wrote through the contact form.", "/admin/website/messages");
        return Map.of("message", thanks);
    }

    // ---- directory -----------------------------------------------------------------------------------------

    public record DirectoryRequest(String fullName, WebMember.HomeState homeState, String homeDistrict, String currentCountry,
                                   String currentCity, String profession, String organization, String email, String phone,
                                   Long chapterId, boolean showContact, String website) {
    }

    @PostMapping("/directory")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, String> register(@RequestBody DirectoryRequest r, HttpServletRequest request) {
        String thanks = "Thank you! Your entry will appear in the directory once it is verified.";
        if (bot(r.website())) return Map.of("message", thanks);
        WebMember m = new WebMember();
        m.setFullName(WebsiteSupport.text(r.fullName(), "Full name", 120, true));
        if (r.homeState() == null) throw WebsiteSupport.bad("Home state is required.");
        m.setHomeState(r.homeState());
        m.setHomeDistrict(WebsiteSupport.text(r.homeDistrict(), "Home district", 80, false));
        m.setCurrentCountry(WebsiteSupport.text(r.currentCountry(), "Country", 80, true));
        m.setCurrentCity(WebsiteSupport.text(r.currentCity(), "City", 80, false));
        m.setProfession(WebsiteSupport.text(r.profession(), "Profession", 120, false));
        m.setOrganization(WebsiteSupport.text(r.organization(), "Organisation", 160, false));
        m.setEmail(WebsiteSupport.email(r.email(), "Email", true));
        m.setPhone(WebsiteSupport.text(r.phone(), "Phone", 40, false));
        if (r.chapterId() != null && !chapters.existsById(r.chapterId())) throw WebsiteSupport.bad("Unknown chapter.");
        m.setChapterId(r.chapterId());
        m.setShowContact(r.showContact());
        m.setApproved(false);
        if (members.existsByEmailIgnoreCase(m.getEmail())) throw WebsiteSupport.bad("This email is already registered in the directory.");
        limit(directoryLimit, request, "You have signed up several times already. Please try again in an hour.");
        members.save(m);
        notifier.notify(Section.WEBSITE_DIRECTORY, m.getChapterId(), null, "Directory sign-up: " + m.getFullName(),
                m.getFullName() + " (" + m.getCurrentCountry() + ") asked to join the directory.", "/admin/website/directory");
        return Map.of("message", thanks);
    }

    // ---- "Write for us" ------------------------------------------------------------------------------------

    @PostMapping(value = "/blog/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, String> submitPost(@RequestParam(required = false) String authorName,
                                          @RequestParam(required = false) String authorEmail,
                                          @RequestParam(required = false) String title,
                                          @RequestParam(required = false) String content,
                                          @RequestParam(required = false) String summary,
                                          @RequestParam(required = false) String topic,
                                          @RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(name = "website", required = false) String trap,
                                          @RequestPart(name = "photo", required = false) MultipartFile photo,
                                          HttpServletRequest request) {
        String thanks = "Thank you! Your post has been sent. An admin will review it before it appears on the site.";
        if (bot(trap)) return Map.of("message", thanks);
        WebBlogPost p = new WebBlogPost();
        p.setAuthor(WebsiteSupport.text(authorName, "Your name", 120, true));
        p.setAuthorEmail(WebsiteSupport.email(authorEmail, "Your email", true).toLowerCase(Locale.ROOT));
        p.setTitle(WebsiteSupport.text(title, "Title", 200, true));
        p.setContent(WebsiteSupport.text(content, "Your post", 20_000, true));
        p.setSummary(WebsiteSupport.text(summary, "Summary", 600, false));
        String t = WebsiteSupport.text(topic, "Topic", 80, false);
        p.setTopic(t == null ? "Community" : t);
        if (projectId != null && !projects.existsById(projectId)) throw WebsiteSupport.bad("Unknown project.");
        p.setProjectId(projectId);
        // a visitor picks one of the shown (active) categories; otherwise it is an article
        p.setCategoryId((categoryId == null ? java.util.Optional.<WebBlogCategory>empty() : categories.findById(categoryId))
                .filter(WebBlogCategory::isActive).or(() -> categories.findBySlug("articles")).map(WebBlogCategory::getId).orElse(null));
        p.setSource(WebBlogPost.Source.PUBLIC);
        p.setReviewRequested(true);
        p.setPublished(false);
        p.setSlug(uniqueSlug(p.getTitle()));
        limit(blogLimit, request, "You have sent several posts already. Please try again in an hour.");
        if (photo != null && !photo.isEmpty()) {
            var stored = storage.store(photo, MAX_PHOTO);
            WebGalleryImage img = new WebGalleryImage();
            img.setTitle(("Knowledge Hub: " + p.getTitle()).substring(0, Math.min(200, p.getTitle().length() + 15)));
            img.setAlbum("Knowledge Hub submissions");
            img.setPublished(false); // not in the public gallery; an admin decides
            img.setFileName(stored.fileName());
            img.setContentType(stored.contentType());
            img.setSizeBytes(stored.size());
            images.save(img);
            p.setCoverImageUrl(img.getUrl());
        }
        posts.save(p);
        notifier.notify(Section.WEBSITE_BLOG_PUBLISH, null, null, "Knowledge Hub post to review: " + p.getTitle(),
                p.getAuthor() + " sent a post through \"Write for us\".", "/admin/website/blog");
        return Map.of("message", thanks);
    }

    private String uniqueSlug(String title) {
        String base = WebsiteSupport.slugify(title);
        String slug = base;
        while (posts.existsBySlug(slug)) slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        return slug;
    }

    // ---- "Volunteer with us" -------------------------------------------------------------------------------

    /**
     * The website's volunteer form (mentor, tutor, sponsor, join a chapter): stored as a volunteer application and
     * followed up in Admin > Volunteers. Lists (subjects, grades, fields) arrive as arrays.
     */
    @PostMapping("/volunteer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> volunteer(@RequestBody Map<String, Object> form, HttpServletRequest request) {
        String thanks = "Thank you for offering to help! Our team will contact you soon.";
        if (bot(form.get("website") == null ? null : String.valueOf(form.get("website")))) return Map.of("message", thanks);
        if (!Boolean.TRUE.equals(form.get("consent"))) throw WebsiteSupport.bad("Please agree to be contacted.");
        limit(volunteerLimit, request, "You have signed up several times already. Please try again in an hour.");
        Map<String, Object> body = new HashMap<>(form);
        body.remove("consent");
        body.put("source", "biharanjuman.com");
        volunteers.submit(body);
        return Map.of("message", thanks);
    }
}
