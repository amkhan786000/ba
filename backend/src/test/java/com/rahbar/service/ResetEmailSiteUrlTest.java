package com.rahbar.service;

import com.rahbar.dto.RenderedEmail;
import com.rahbar.repository.EmailTemplateRepository;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Password reset emails say where to sign in: the site's address. */
class ResetEmailSiteUrlTest {

    private final EmailTemplateRepository templates = mock(EmailTemplateRepository.class);
    private final EmailTemplateService service = new EmailTemplateService(templates, mock(UserRepository.class));

    ResetEmailSiteUrlTest() {
        when(templates.findById(anyString())).thenReturn(Optional.empty()); // the built-in wording
        ReflectionTestUtils.setField(service, "siteUrl", "https://rahbar.example.org/, http://localhost:4200");
    }

    @Test
    void theAdminResetEmailHasTheSignInAddress() {
        RenderedEmail mail = service.render(EmailType.PASSWORD_RESET_BY_ADMIN, Map.of("name", "Asha", "password", "Tmp-123"));
        assertTrue(mail.body().contains("Tmp-123"));
        assertTrue(mail.body().contains("Sign in at: https://rahbar.example.org/login"), mail.body());
    }

    @Test
    void theForgotPasswordEmailHasItToo() {
        RenderedEmail mail = service.render(EmailType.PASSWORD_RESET_CODE, Map.of("name", "Asha", "code", "482913", "minutes", 15));
        assertTrue(mail.body().contains("482913"));
        assertTrue(mail.body().contains("https://rahbar.example.org/login"), mail.body());
    }

    @Test
    void withoutASiteAddressItStillReadsWell() {
        assertEquals("the Rahbar website", EmailTemplateService.signInUrl(""));
        assertEquals("https://a.org/login", EmailTemplateService.signInUrl("https://a.org"));
    }
}
