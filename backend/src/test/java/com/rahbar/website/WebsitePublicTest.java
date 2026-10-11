package com.rahbar.website;

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
    void imagesAreRecognisedByTheirBytes() {
        assertEquals("jpg", WebsiteImageStorage.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("png", WebsiteImageStorage.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}));
        assertNull(WebsiteImageStorage.detect("<html>".getBytes()));
    }
}
