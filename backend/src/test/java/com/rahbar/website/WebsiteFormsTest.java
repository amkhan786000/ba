package com.rahbar.website;

import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.security.Section;
import com.rahbar.service.VolunteerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The website's public forms: checks, bot traps, limits per visitor and staff notifications. */
class WebsiteFormsTest {

    private final WebMessageRepository messages = mock(WebMessageRepository.class);
    private final WebMemberRepository members = mock(WebMemberRepository.class);
    private final WebBlogPostRepository posts = mock(WebBlogPostRepository.class);
    private final WebProjectRepository projects = mock(WebProjectRepository.class);
    private final WebGalleryImageRepository images = mock(WebGalleryImageRepository.class);
    private final ChapterRepository chapters = mock(ChapterRepository.class);
    private final WebsiteNotifier notifier = mock(WebsiteNotifier.class);
    private final VolunteerService volunteers = mock(VolunteerService.class);
    private final WebsiteFormsController forms = new WebsiteFormsController(messages, members, posts, projects, images, chapters,
            mock(WebsiteImageStorage.class), notifier, volunteers, mock(WebBlogCategoryRepository.class));

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Forwarded-For", ip + ", 10.0.0.1");
        return r;
    }

    private static WebsiteFormsController.ContactRequest contact(String trap) {
        return new WebsiteFormsController.ContactRequest(" Sana ", "sana@example.com", null, null, "Sponsoring", "Hello", trap);
    }

    @Test
    void contactMessagesAreSavedAndStaffNotified() {
        forms.contact(contact(null), from("1.1.1.1"));
        ArgumentCaptor<WebMessage> saved = ArgumentCaptor.forClass(WebMessage.class);
        verify(messages).save(saved.capture());
        assertEquals("Sana", saved.getValue().getName());
        assertEquals(WebMessage.Topic.GENERAL, saved.getValue().getTopic());
        verify(notifier).notify(eq(Section.WEBSITE_MESSAGES), isNull(), isNull(), contains("Sponsoring"), anyString(), eq("/admin/website/messages"));
    }

    @Test
    void botsArePretendedToAndFloodsStopped() {
        forms.contact(contact("http://spam"), from("2.2.2.2"));
        verify(messages, never()).save(any());

        for (int i = 0; i < 5; i++) forms.contact(contact(null), from("3.3.3.3"));
        ApiException e = assertThrows(ApiException.class, () -> forms.contact(contact(null), from("3.3.3.3")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        forms.contact(contact(null), from("4.4.4.4")); // another visitor is fine
        verify(messages, times(6)).save(any());
    }

    @Test
    void contactNeedsAValidEmailAndMessage() {
        var bad = new WebsiteFormsController.ContactRequest("Sana", "not-an-email", null, null, "Hi", "Hello", null);
        assertThrows(ApiException.class, () -> forms.contact(bad, from("5.5.5.5")));
        var empty = new WebsiteFormsController.ContactRequest("Sana", "sana@example.com", null, null, "Hi", " ", null);
        assertThrows(ApiException.class, () -> forms.contact(empty, from("5.5.5.5")));
        verify(messages, never()).save(any());
    }

    @Test
    void directorySignUpsWaitForApprovalAndEmailsAreUnique() {
        when(chapters.existsById(3L)).thenReturn(true);
        var r = new WebsiteFormsController.DirectoryRequest("Imran Khan", WebMember.HomeState.BIHAR, "Darbhanga", "Saudi Arabia",
                "Riyadh", null, null, "imran@example.com", null, 3L, true, null);
        forms.register(r, from("6.6.6.6"));
        ArgumentCaptor<WebMember> saved = ArgumentCaptor.forClass(WebMember.class);
        verify(members).save(saved.capture());
        assertFalse(saved.getValue().isApproved());
        verify(notifier).notify(eq(Section.WEBSITE_DIRECTORY), eq(3L), isNull(), anyString(), anyString(), anyString());

        when(members.existsByEmailIgnoreCase("imran@example.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> forms.register(r, from("7.7.7.7")));
    }

    @Test
    void volunteersNeedConsentAndAreSentOnWithTheSource() {
        Map<String, Object> form = new HashMap<>(Map.of("fullName", "Sana", "email", "s@example.com", "wantsTutor", true,
                "tutorSubjects", List.of("Maths")));
        assertThrows(ApiException.class, () -> forms.volunteer(form, from("8.8.8.8")));
        verifyNoInteractions(volunteers);

        form.put("consent", true);
        forms.volunteer(form, from("8.8.8.8"));
        ArgumentCaptor<Map<String, Object>> sent = ArgumentCaptor.forClass(Map.class);
        verify(volunteers).submit(sent.capture());
        assertEquals("biharanjuman.com", sent.getValue().get("source"));
        assertFalse(sent.getValue().containsKey("consent"));
    }
}
