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
    private final GranteeDetailsRepository applications = mock(GranteeDetailsRepository.class);
    private final DataQualityService service = new DataQualityService(users, mock(GrantorGranteeRepository.class),
            mock(BankDetailsRepository.class), mock(StudentInstitutionCourseRepository.class), applications,
            mock(PaymentRepository.class), mock(ChapterRepository.class), roles, centers, volunteers, messages,
            mock(WebMemberRepository.class), posts, mock(WebStoryRepository.class), projects, mock(PaymentScheduleRepository.class));

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
        assertEquals(31, checks.size()); // 23 before, duplicate sponsors, 7 payment set-up checks
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

    private static com.rahbar.entity.GranteeDetails application(long userId, String father, String mobile) {
        var g = new com.rahbar.entity.GranteeDetails();
        g.setGranteeDetailId(userId);
        g.setUserId(userId);
        g.setFatherName(father);
        g.setStudentMobile(mobile);
        return g;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> check) {
        return (List<Map<String, Object>>) check.get("rows");
    }

    @Test
    void aSameNameAloneIsNotADuplicate() {
        User a = user(30, 6, "S-30"); a.setName("Ayesha Khan"); a.setPhone("9876543210");
        User b = user(31, 6, "S-31"); b.setName("ayesha  khan"); b.setPhone("+91 98765 43210"); // same name and phone
        User c = user(32, 6, "S-32"); c.setName("Ayesha Khan"); // same name only, different father
        User d = user(33, 6, "S-33"); d.setName("Aysha Khan"); // different spelling, same father and mobile as e
        User e = user(34, 6, "S-34"); e.setName("Ayesha Begum");
        when(users.findByRoleId(6)).thenReturn(List.of(a, b, c, d, e));
        when(applications.findAll()).thenReturn(List.of(application(30, "Imran", null), application(32, "Salim", null),
                application(33, "Md. Rafiq", "9000000001"), application(34, "md rafiq", "9000000001")));

        var check = byKey().get("possible_duplicate_students");
        assertEquals(4, check.get("count")); // a+b (name, phone), d+e (father, phone); c is not flagged
        var r = rows(check);
        assertEquals(Set.of(30L, 31L, 33L, 34L), new HashSet<>(r.stream().map(x -> (Long) x.get("id")).toList()));
        assertEquals(r.get(0).get("group"), r.get(1).get("group"));
        assertNotEquals(r.get(0).get("group"), r.get(2).get("group"));
        assertEquals("name, phone", r.get(0).get("matched_on"));
    }

    @Test
    void sponsorsMatchOnNamePlusEmailPhoneOrChapter() {
        User a = user(40, 5, "SP-40"); a.setName("Abdul Rahman"); a.setChapterId(2L);
        User b = user(41, 5, "SP-41"); b.setName("Abdul Rahman"); b.setChapterId(2L); // same name and chapter
        User c = user(42, 5, "SP-42"); c.setName("Abdul Rahman"); c.setChapterId(3L); // same name, other chapter
        User d = user(43, 5, "SP-43"); d.setName("Zaid"); d.setEmail("zaid@example.com"); d.setChapterId(5L);
        User e = user(44, 5, "SP-44"); e.setName("Zaid"); e.setEmail("ZAID@example.com "); e.setChapterId(6L); // same name and email
        when(users.findByRoleId(5)).thenReturn(List.of(a, b, c, d, e));

        var check = byKey().get("possible_duplicate_sponsors");
        assertEquals(Set.of(40L, 41L, 43L, 44L), new HashSet<>(rows(check).stream().map(x -> (Long) x.get("id")).toList()));
    }
}
