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
        signIn(roleId, "DOCUMENTS:EDIT");
    }

    private static void signIn(int roleId, String... permissions) {
        User u = new User();
        u.setId(1L);
        u.setRoleId(roleId);
        var p = new RahbarUserPrincipal(u, Set.of(permissions), Role.SCOPE_ALL);
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

    private static OrgDocument doc(String visibility, String roles, boolean archived) {
        OrgDocument d = new OrgDocument();
        d.setVisibility(visibility);
        d.setVisibleRoleIds(roles);
        d.setArchived(archived);
        return d;
    }

    @Test
    void readersAreChosenOnEachDocument() {
        signIn(5, "DASHBOARD:VIEW"); // a sponsor without any documents permission
        assertTrue(OrgDocumentService.mayRead(doc("PUBLIC", null, false)));
        assertFalse(OrgDocumentService.mayRead(doc("ROLES", "2,8", false)));
        assertTrue(OrgDocumentService.mayRead(doc("ROLES", "2,5", false)));
        assertFalse(OrgDocumentService.mayRead(doc("PUBLIC", null, true)));   // archived: editors only
        assertFalse(OrgDocumentService.mayRead(doc("ROLES", null, false)));   // no roles chosen yet: editors only

        signIn(8, "DOCUMENTS:EDIT"); // editors see everything
        assertTrue(OrgDocumentService.mayRead(doc("ROLES", "2", true)));
    }

    @Test
    void aNewDocumentIsForEveryoneUnlessRolesAreChosen() {
        assertEquals("PUBLIC", service.save(null, body("Policy", "Text")).getVisibility());
        Map<String, Object> roles = body("Board minutes", "Text");
        roles.put("visibility", "ROLES");
        assertThrows(ApiException.class, () -> service.save(null, roles)); // no role chosen
        roles.put("visibleRoleIds", List.of(2, 8));
        OrgDocument d = service.save(null, roles);
        assertEquals("ROLES", d.getVisibility());
        assertEquals(Set.of(2, 8), d.visibleRoles());
    }

    @Test
    void searchLooksInTheTextAndShowsWhereItMatched() {
        OrgDocument byLaws = existing(); // "Article 1\nThe name is Bihar Anjuman."
        byLaws.setVisibility("PUBLIC");
        OrgDocument policy = new OrgDocument();
        policy.setId(8L);
        policy.setCategoryId(3L);
        policy.setTitle("Travel policy");
        policy.setContent("Trains only.");
        policy.setVisibility("PUBLIC");
        when(documents.findAllByOrderByTitleAsc()).thenReturn(List.of(byLaws, policy));
        when(revisions.findAll()).thenReturn(List.of());
        when(categories.findAllByOrderByDisplayOrderAscNameAsc()).thenReturn(List.of());
        when(documents.findAll()).thenReturn(List.of(byLaws, policy));

        @SuppressWarnings("unchecked") List<Map<String, Object>> found = (List<Map<String, Object>>) service.list("bihar ARTICLE").get("documents");
        assertEquals(1, found.size());
        assertEquals("By-laws", found.get(0).get("title"));
        assertTrue(String.valueOf(found.get(0).get("snippet")).startsWith("Article 1"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> all = (List<Map<String, Object>>) service.list("  ").get("documents");
        assertEquals(2, all.size());
        assertEquals("…the end", OrgDocumentService.snippet("x".repeat(100) + " the end", List.of("end")).replaceAll("x+ ", ""));
    }
}
