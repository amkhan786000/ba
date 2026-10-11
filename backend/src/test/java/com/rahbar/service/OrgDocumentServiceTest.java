package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.OrgDocumentCategoryRepository;
import com.rahbar.repository.OrgDocumentRepository;
import com.rahbar.repository.OrgDocumentRevisionRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Admin > Documents: earlier versions are kept, archive instead of delete, categories in use stay. */
class OrgDocumentServiceTest {

    private final OrgDocumentRepository documents = mock(OrgDocumentRepository.class);
    private final OrgDocumentCategoryRepository categories = mock(OrgDocumentCategoryRepository.class);
    private final OrgDocumentRevisionRepository revisions = mock(OrgDocumentRevisionRepository.class);
    private final OrgDocumentService service = new OrgDocumentService(documents, categories, revisions, mock(UserRepository.class));

    private static void signIn(int roleId) {
        User u = new User();
        u.setId(1L);
        u.setRoleId(roleId);
        var p = new RahbarUserPrincipal(u, Set.of("DOCUMENTS:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @BeforeEach
    void setUp() {
        signIn(2);
        when(categories.existsById(3L)).thenReturn(true);
        when(documents.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private OrgDocument existing() {
        OrgDocument d = new OrgDocument();
        d.setId(7L);
        d.setCategoryId(3L);
        d.setTitle("By-laws");
        d.setContent("Article 1\nThe name is Bihar Anjuman.");
        when(documents.findById(7L)).thenReturn(Optional.of(d));
        return d;
    }

    private static Map<String, Object> body(String title, String content) {
        Map<String, Object> b = new HashMap<>();
        b.put("categoryId", 3);
        b.put("title", title);
        b.put("content", content);
        return b;
    }

    @Test
    void theTextIsKeptAsPastedAndAChangeKeepsTheOldVersion() {
        existing();
        OrgDocument saved = service.save(7L, body("By-laws", "Article 1\r\nThe name is Bihar Anjuman.\r\n\r\nArticle 2\n  Members.\n\n"));
        assertEquals("Article 1\nThe name is Bihar Anjuman.\n\nArticle 2\n  Members.", saved.getContent());
        ArgumentCaptor<OrgDocumentRevision> r = ArgumentCaptor.forClass(OrgDocumentRevision.class);
        verify(revisions).save(r.capture());
        assertEquals("Article 1\nThe name is Bihar Anjuman.", r.getValue().getContent());
        assertEquals(7L, r.getValue().getDocumentId());
    }

    @Test
    void savingWithoutChangesKeepsNoVersion() {
        existing();
        service.save(7L, body("By-laws", "Article 1\nThe name is Bihar Anjuman."));
        verify(revisions, never()).save(any());
    }

    @Test
    void aCategoryAndTextAreRequired() {
        Map<String, Object> noCategory = body("Policy", "Text");
        noCategory.put("categoryId", 99);
        assertThrows(ApiException.class, () -> service.save(null, noCategory));
        assertThrows(ApiException.class, () -> service.save(null, body("Policy", "   ")));
    }

    @Test
    void onlyTheSuperAdminDeletes() {
        existing();
        assertThrows(ApiException.class, () -> service.delete(7L));
        verify(documents, never()).delete(any());
        signIn(Role.SUPER_ADMIN);
        service.delete(7L);
        verify(documents).delete(any());
    }

    @Test
    void restoringKeepsTheReplacedText() {
        existing();
        OrgDocumentRevision old = new OrgDocumentRevision();
        old.setId(4L);
        old.setDocumentId(7L);
        old.setTitle("By-laws (2019)");
        old.setContent("Old article 1");
        when(revisions.findById(4L)).thenReturn(Optional.of(old));
        OrgDocument now = service.restore(7L, 4L);
        assertEquals("Old article 1", now.getContent());
        assertEquals("By-laws (2019)", now.getTitle());
        verify(revisions).save(any()); // the text that was replaced
    }

    @Test
    void aCategoryWithDocumentsCannotBeDeleted() {
        OrgDocumentCategory c = new OrgDocumentCategory();
        c.setId(3L);
        c.setName("By-laws");
        when(categories.findById(3L)).thenReturn(Optional.of(c));
        when(documents.existsByCategoryId(3L)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.deleteCategory(3L));
        verify(categories, never()).delete(any());
    }
}
