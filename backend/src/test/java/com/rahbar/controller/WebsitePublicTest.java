package com.rahbar.controller;

import com.rahbar.entity.WebBlogPost;
import com.rahbar.entity.WebGalleryImage;
import com.rahbar.entity.WebMember;
import com.rahbar.entity.WebStory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What the website may show: privacy rules on stories and the directory, no visitor emails on blog posts. */
class WebsitePublicTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void storiesShowFirstNameAndInitialAndHidePhotoUnlessAllowed() {
        WebStory s = new WebStory();
        s.setPersonName("Sana Parveen Khatoon");
        s.setPhotoUrl("/api/images/abc.jpg");
        s.setShowPhoto(false);
        var view = WebsitePublicController.StoryView.of(s);
        assertEquals("Sana K.", view.name());
        assertNull(view.photoUrl());

        s.setShowFullName(true);
        s.setShowPhoto(true);
        view = WebsitePublicController.StoryView.of(s);
        assertEquals("Sana Parveen Khatoon", view.name());
        assertEquals("/api/images/abc.jpg", view.photoUrl());
    }

    @Test
    void directoryShowsContactDetailsOnlyWhenAllowed() {
        WebMember m = new WebMember();
        m.setFullName("Imran Khan");
        m.setEmail("imran@example.com");
        m.setPhone("+966500000000");
        var entry = WebsitePublicController.DirectoryEntry.of(m);
        assertNull(entry.email());
        assertNull(entry.phone());
        m.setShowContact(true);
        assertEquals("imran@example.com", WebsitePublicController.DirectoryEntry.of(m).email());
    }

    @Test
    void blogPostsNeverCarryTheVisitorsEmail() throws Exception {
        WebBlogPost p = new WebBlogPost();
        p.setTitle("Hello");
        p.setAuthorEmail("visitor@example.com");
        String out = json.writeValueAsString(WebsitePublicController.PostView.of(p));
        assertFalse(out.contains("visitor@example.com"), out);
        assertFalse(out.contains("createdBy"), out);
    }

    @Test
    void galleryUrlsWorkOnTheWebsiteAndHere() {
        WebGalleryImage g = new WebGalleryImage();
        g.setFileName("0123456789abcdef0123456789abcdef.jpg");
        assertEquals("/api/images/0123456789abcdef0123456789abcdef.jpg", g.getUrl());
    }

    @Test
    void postsShowTheirCategoryAndCopeWithoutOne() {
        WebBlogPost p = new WebBlogPost();
        p.setCategoryId(2L);
        p.setMediaUrl("https://www.youtube.com/watch?v=abc");
        var view = WebsitePublicController.PostView.of(p, java.util.Map.of(2L, new WebsitePublicController.CategoryRef("research", "Research")));
        assertEquals("research", view.category().slug());
        assertEquals("https://www.youtube.com/watch?v=abc", view.mediaUrl());
        p.setCategoryId(null);
        assertNull(WebsitePublicController.PostView.of(p, java.util.Map.of()).category());
    }

    @Test
    void documentAndMediaLinksAreChecked() {
        assertEquals("/api/documents/0123456789abcdef0123456789abcdef.pdf",
                WebsiteAdminPostsController.document("/api/documents/0123456789abcdef0123456789abcdef.pdf"));
        assertThrows(com.rahbar.exception.ApiException.class, () -> WebsiteAdminPostsController.document("https://elsewhere.example/x.pdf"));
        assertThrows(com.rahbar.exception.ApiException.class, () -> WebsiteAdminPostsController.mediaLink("javascript:alert(1)"));
        assertEquals("https://youtu.be/abc", WebsiteAdminPostsController.mediaLink(" https://youtu.be/abc "));
    }
}
