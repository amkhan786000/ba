package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Chapters: the lead email field may hold several addresses separated by "|". */
class ChapterServiceTest {

    private final ChapterRepository chapters = mock(ChapterRepository.class);
    private final ChapterService service = new ChapterService(chapters, mock(UserRepository.class));

    @BeforeEach
    void signIn() {
        User admin = new User();
        admin.setId(1L);
        admin.setRoleId(1);
        var principal = new RahbarUserPrincipal(admin, Set.of("CHAPTERS:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Chapter saved(String leadEmail) {
        when(chapters.findByChapterNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(chapters.save(any())).thenAnswer(i -> i.getArgument(0));
        Chapter c = new Chapter();
        c.setChapterName("Jeddah");
        c.setLeadEmail(leadEmail);
        return service.save(c);
    }

    @Test
    void severalLeadEmailsAreAcceptedAndTidied() {
        Chapter c = saved(" a@example.com|b@example.com |  | c@example.com ");
        assertEquals("a@example.com | b@example.com | c@example.com", c.getLeadEmail());
        assertEquals(List.of("a@example.com", "b@example.com", "c@example.com"), c.leadEmails());
    }

    @Test
    void theFieldIsNotCheckedAsOneEmail() {
        assertEquals("not an email", saved("not an email").getLeadEmail());
        assertNull(saved(" | ").getLeadEmail());
        assertNull(saved(null).getLeadEmail());
        assertEquals(List.of(), new Chapter().leadEmails());
    }
}
