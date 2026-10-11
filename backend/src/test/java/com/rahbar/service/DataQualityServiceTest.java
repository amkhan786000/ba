package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.RccCenter;
import com.rahbar.entity.User;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.repository.*;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.website.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Data Quality: grouped like the menu, coaching-center students kept out of scholarship checks, the new checks. */
class DataQualityServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RccCenterRepository centers = mock(RccCenterRepository.class);
    private final VolunteerApplicationRepository volunteers = mock(VolunteerApplicationRepository.class);
    private final WebMessageRepository messages = mock(WebMessageRepository.class);
    private final WebBlogPostRepository posts = mock(WebBlogPostRepository.class);
    private final WebProjectRepository projects = mock(WebProjectRepository.class);
    private final DataQualityService service = new DataQualityService(users, mock(GrantorGranteeRepository.class),
            mock(BankDetailsRepository.class), mock(StudentInstitutionCourseRepository.class), mock(GranteeDetailsRepository.class),
            mock(PaymentRepository.class), mock(ChapterRepository.class), roles, centers, volunteers, messages,
            mock(WebMemberRepository.class), posts, mock(WebStoryRepository.class), projects);

    private static User user(long id, int role, String ref) {
        User u = new User();
        u.setId(id);
        u.setRoleId(role);
        u.setUserId(ref);
        u.setName("User " + id);
        u.setStatus("Active");
        return u;
    }

    private static Role role(int id, String name, String scope) {
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleName(name);
        r.setScope(scope);
        return r;
    }

    @BeforeEach
    void signIn() {
        var principal = new RahbarUserPrincipal(user(1, 1, "1001"), Set.of("DATA_QUALITY:VIEW"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Map<String, Map<String, Object>> byKey() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        service.checks().forEach(c -> out.put((String) c.get("key"), c));
        return out;
    }

    @Test
    void everyCheckHasAMenuGroupAndAScreen() {
        Set<String> groups = Set.of("students", "sponsors", "community", "website", "administration");
        var checks = service.checks();
        assertEquals(23, checks.size()); // 11 before, 12 new
        for (var c : checks) {
            assertTrue(groups.contains((String) c.get("group")), c.get("key") + " has group " + c.get("group"));
            assertTrue(String.valueOf(c.get("link")).startsWith("/admin/"), c.get("key") + " has link " + c.get("link"));
        }
    }

    @Test
    void coachingCenterStudentsWithoutAReferenceAreNotScholarshipProblems() {
        User scholar = user(10, 6, "STU-10");
        User coaching = user(11, 6, null);
        coaching.setRccCenterId(3L);
        when(users.findByRoleId(6)).thenReturn(List.of(scholar, coaching));
        RccCenter c = new RccCenter();
        c.setRccCenterId(3L);
        c.setCenterName("RCC Hazaribagh");
        when(centers.findAll()).thenReturn(List.of(c));

        var checks = byKey();
        assertEquals(1, checks.get("students_without_sponsor").get("count")); // only the scholarship student
        assertEquals(1, checks.get("students_without_bank").get("count"));
        assertEquals(1, checks.get("rcc_students_without_reference").get("count"));
        assertEquals(1, checks.get("rcc_centers_without_incharge").get("count")); // no in-charge, no number
    }

    @Test
    void scopedLeadsMustBeLinkedToTheirChapterOrCenter() {
        when(roles.findAll()).thenReturn(List.of(role(9, "Chapter Lead", Role.SCOPE_CHAPTER), role(10, "RCC Coordinator", Role.SCOPE_RCC)));
        User linked = user(20, 9, "L-20");
        linked.setChapterId(4L);
        User unlinked = user(21, 9, "L-21");
        User coordinator = user(22, 10, "R-22");
        User inactive = user(23, 9, "L-23");
        inactive.setStatus("Inactive");
        when(users.findAll()).thenReturn(List.of(linked, unlinked, coordinator, inactive));

        var checks = byKey();
        assertEquals(1, checks.get("chapter_leads_without_chapter").get("count"));
        assertEquals(1, checks.get("rcc_coordinators_without_center").get("count"));
    }

    @Test
    void followUpsCountOnlyAfterAWeekAndContentGapsAreFound() {
        VolunteerApplication old = new VolunteerApplication();
        old.setFullName("Asma");
        old.setStatus("NEW");
        old.setWantsTutor(true);
        old.setCreatedAt(LocalDateTime.now().minusDays(10));
        VolunteerApplication recent = new VolunteerApplication();
        recent.setStatus("NEW");
        recent.setCreatedAt(LocalDateTime.now().minusDays(2));
        VolunteerApplication contacted = new VolunteerApplication();
        contacted.setStatus("CONTACTED");
        contacted.setCreatedAt(LocalDateTime.now().minusDays(30));
        when(volunteers.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(old, recent, contacted));

        WebMessage open = new WebMessage();
        open.setCreatedAt(LocalDateTime.now().minusDays(8));
        WebMessage handled = new WebMessage();
        handled.setHandled(true);
        handled.setCreatedAt(LocalDateTime.now().minusDays(8));
        when(messages.findAll()).thenReturn(List.of(open, handled));

        WebProject withImage = new WebProject();
        withImage.setImageUrl("/api/images/a.jpg");
        WebProject without = new WebProject();
        WebProject inactive = new WebProject();
        inactive.setActive(false);
        when(projects.findAll()).thenReturn(List.of(withImage, without, inactive));

        WebBlogPost waiting = new WebBlogPost();
        waiting.setReviewRequested(true);
        waiting.setUpdatedAt(LocalDateTime.now().minusDays(9));
        WebBlogPost published = new WebBlogPost();
        published.setPublished(true);
        published.setSummary(" ");
        when(posts.findAll()).thenReturn(List.of(waiting, published));

        var checks = byKey();
        assertEquals(1, checks.get("volunteers_waiting").get("count"));
        assertEquals(1, checks.get("website_messages_waiting").get("count"));
        assertEquals(1, checks.get("projects_without_image").get("count"));
        assertEquals(1, checks.get("blog_posts_waiting_review").get("count"));
        assertEquals(1, checks.get("blog_posts_without_summary").get("count"));
    }
}
