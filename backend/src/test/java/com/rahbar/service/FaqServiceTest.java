package com.rahbar.service;

import com.rahbar.entity.Faq;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.FaqRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Help & FAQ: who reads which questions, who edits, and what the website shows. */
class FaqServiceTest {

    private final FaqRepository faqs = mock(FaqRepository.class);
    private final FaqService service = new FaqService(faqs);

    private static Faq faq(long id, String audience, boolean active, boolean website) {
        Faq f = new Faq();
        f.setId(id);
        f.setCategory("C");
        f.setAudience(audience);
        f.setQuestion("Q" + id);
        f.setAnswer("A" + id);
        f.setActive(active);
        f.setOnWebsite(website);
        return f;
    }

    private static void signIn(Set<Integer> roles, String... permissions) {
        User u = new User();
        u.setId(1L);
        u.setRoleId(roles.iterator().next());
        var p = new RahbarUserPrincipal(u, Set.of(permissions), Role.SCOPE_ALL, roles, false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @BeforeEach
    void setUp() {
        when(faqs.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(
                faq(1, "ALL", true, true), faq(2, "STAFF", true, false), faq(3, "SPONSORS", true, false),
                faq(4, "STUDENTS", true, false), faq(5, "ALL", false, false)));
        when(faqs.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private List<Object> ids(Map<String, Object> body) {
        return ((List<Map<String, Object>>) body.get("faqs")).stream().map(r -> r.get("id")).toList();
    }

    @Test
    void everyoneReadsTheQuestionsForTheirRoles() {
        signIn(Set.of(5));
        assertEquals(List.of(1L, 3L), ids(service.list(null)));
        signIn(Set.of(6));
        assertEquals(List.of(1L, 4L), ids(service.list(null)));
        signIn(Set.of(9), "STUDENTS:VIEW"); // a chapter lead: staff
        assertEquals(List.of(1L, 2L), ids(service.list(null)));
    }

    @Test
    void editorsSeeEverythingButAPortalShowsWhatThatRoleReads() {
        signIn(Set.of(8, 5), "FAQS:EDIT"); // office coordinator who also sponsors
        Map<String, Object> all = service.list(null);
        assertEquals(true, all.get("editor"));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), ids(all));
        Map<String, Object> asSponsor = service.list(5);
        assertEquals(false, asSponsor.get("editor"));
        assertEquals(List.of(1L, 3L), ids(asSponsor));
        assertThrows(ApiException.class, () -> service.list(6));
    }

    @Test
    void theWebsiteGetsOnlyActiveSwitchedOnQuestions() {
        when(faqs.findByActiveTrueAndOnWebsiteTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(faq(1, "ALL", true, true)));
        List<Map<String, Object>> site = service.website();
        assertEquals(1, site.size());
        assertEquals(Set.of("id", "category", "question", "answer"), site.get(0).keySet());
    }

    @Test
    void aNewFaqIsOffTheWebsiteUnlessSwitchedOnAndNeedsQuestionAndAnswer() {
        Faq f = service.save(null, Map.of("category", "Payments", "question", "How?", "answer", "Like this."));
        assertFalse(f.isOnWebsite());
        assertTrue(f.isActive());
        assertEquals("ALL", f.getAudience());
        assertEquals(10, f.getDisplayOrder());
        assertThrows(ApiException.class, () -> service.save(null, Map.of("category", "Payments", "question", " ", "answer", "x")));
    }
}
