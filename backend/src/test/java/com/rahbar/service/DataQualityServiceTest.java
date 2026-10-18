package com.rahbar.service;

import com.rahbar.entity.WebBlogPost;
import com.rahbar.repository.WebBlogPostRepository;
import com.rahbar.repository.WebMemberRepository;
import com.rahbar.entity.WebMessage;
import com.rahbar.repository.WebMessageRepository;
import com.rahbar.entity.WebProject;
import com.rahbar.repository.WebProjectRepository;
import com.rahbar.repository.WebStoryRepository;

import com.rahbar.entity.Role;
import com.rahbar.entity.RccCenter;
import com.rahbar.entity.User;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.repository.*;
import com.rahbar.security.RahbarUserPrincipal;
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
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final DataQualityService service = new DataQualityService(users, mappings,
            mock(BankDetailsRepository.class), mock(StudentInstitutionCourseRepository.class), applications,
            payments, mock(ChapterRepository.class), roles, centers, volunteers, messages,
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
        assertEquals(39, checks.size()); // 27 before (incl. sponsor chapter / phone, other chapter, spent proof), duplicate sponsors, 10 payment set-up checks, same person / two accounts
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
    void samePersonWithAccountsInDifferentRolesIsFound() {
        when(roles.findAll()).thenReturn(List.of(role(2, "Application Administrator", Role.SCOPE_ALL), role(5, "Sponsor", Role.SCOPE_ALL),
                role(6, "Student", Role.SCOPE_ALL)));
        User staff = user(30, 2, "A-30"), sponsorAccount = user(31, 5, "S-31");
        staff.setName("Amjad  Khan");
        sponsorAccount.setName("amjad khan");
        User twin1 = user(32, 6, "ST-32"), twin2 = user(33, 6, "ST-33"); // students only: the student check's job
        twin1.setName("Sara Ali");
        twin2.setName("Sara Ali");
        User merged = user(34, 5, "S-34");
        merged.setName("Amjad Khan");
        merged.setMergedIntoId(30L);
        when(users.findAll()).thenReturn(List.of(staff, sponsorAccount, twin1, twin2, merged));

        Map<String, Object> check = byKey().get("same_person_two_accounts");
        assertEquals(2, check.get("count"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) check.get("rows");
        assertEquals(List.of("A-30", "S-31"), rows.stream().map(r -> r.get("user_id")).toList());
        assertEquals("Sponsor", rows.get(1).get("roles"));
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

    private static com.rahbar.entity.GranteeDetails application(long userId, String father, String fatherMobile) {
        var g = new com.rahbar.entity.GranteeDetails();
        g.setGranteeDetailId(userId);
        g.setUserId(userId);
        g.setFatherName(father);
        g.setFatherMobile(fatherMobile);
        return g;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> check) {
        return (List<Map<String, Object>>) check.get("rows");
    }

    @Test
    void aStudentDuplicateNeedsTheNameFatherFatherMobileAndEmail() {
        // a+b: same name, father, father's mobile and email (spelling/format aside)
        User a = user(30, 6, "S-30"); a.setName("Ayesha Khan"); a.setEmail("ayesha@example.com");
        User b = user(31, 6, "S-31"); b.setName("ayesha  khan"); b.setEmail("AYESHA@example.com ");
        // c+d: sisters (different names), same father, his mobile and an email: never a duplicate
        User c = user(32, 6, "PVT078-2025"); c.setName("Rahma Khatun"); c.setEmail("PVT078-2025@rahbar.com");
        User d = user(33, 6, "PVT066-2025"); d.setName("Sana Tabassum"); d.setEmail("PVT066-2025@rahbar.com");
        // e: same name and father as a, other father's mobile
        User e = user(34, 6, "S-34"); e.setName("Ayesha Khan"); e.setEmail("ayesha@example.com");
        // f: same name, father and mobile as a, but another real email
        User f = user(35, 6, "S-35"); f.setName("Ayesha Khan"); f.setEmail("other@example.com");
        // g+h: same name, father and mobile; only placeholder emails, so the email doesn't tell them apart
        User g = user(36, 6, "S-36"); g.setName("Zoya"); g.setEmail("S-36@rahbar.com");
        User h = user(37, 6, "S-37"); h.setName("Zoya");
        when(users.findByRoleId(6)).thenReturn(List.of(a, b, c, d, e, f, g, h));
        when(applications.findAll()).thenReturn(List.of(application(30, "Md. Imran", "9000000001"),
                application(31, "md imran", "+91 90000 00001"), application(32, "MD AKBAR", "6209175137"),
                application(33, "MD AKBAR", "6209175137"), application(34, "Md Imran", "9000000002"),
                application(35, "Md Imran", "9000000001"), application(36, "Salim", "9111111111"),
                application(37, "Salim", "9111111111")));

        var check = byKey().get("possible_duplicate_students");
        var r = rows(check);
        assertEquals(Set.of(30L, 31L, 36L, 37L), new HashSet<>(r.stream().map(x -> (Long) x.get("id")).toList()));
        assertEquals(r.get(0).get("group"), r.get(1).get("group"));
        assertNotEquals(r.get(0).get("group"), r.get(2).get("group"));
        assertEquals("name, father's name, father's mobile, email", r.get(0).get("matched_on"));
        assertEquals("name, father's name, father's mobile", r.get(2).get("matched_on"));
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

    @Test
    void everyCurrentStudentNeedsAPaymentStartDate() {
        User noStart = user(30, 6, "STU-30");
        User withStart = user(31, 6, "STU-31");
        withStart.setPaymentStartDate(java.time.LocalDate.of(2026, 7, 1));
        User coaching = user(32, 6, null); // a coaching-center student without a reference ID is not checked
        coaching.setRccCenterId(3L);
        User graduated = user(33, 6, "STU-33");
        graduated.setStudyStatus("GRADUATED");
        when(users.findByRoleId(6)).thenReturn(List.of(noStart, withStart, coaching, graduated));

        var check = byKey().get("students_without_payment_start");
        assertEquals("sponsors", check.get("group"));
        assertEquals(1, check.get("count"));
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) check.get("rows");
        assertEquals("STU-30", rows.get(0).get("user_id"));
        assertEquals("No", rows.get(0).get("sponsored"));
        assertEquals(0, byKey().get("sponsored_students_without_session_year").get("count")); // nobody is sponsored here
    }

    @Test
    void studentChecksLookAtStudyingStudentsUnlessThePageChoosesOtherStatuses() {
        User studying = user(50, 6, "STU-50");
        User onHold = user(51, 6, "STU-51");
        onHold.setStudyStatus("ON_HOLD");
        User graduated = user(52, 6, "STU-52");
        graduated.setStudyStatus("GRADUATED");
        User legacy = user(53, 6, "STU-53"); // no study status yet: counts as studying
        when(users.findByRoleId(6)).thenReturn(List.of(studying, onHold, graduated, legacy));

        var byDefault = byKey().get("students_without_payment_start");
        assertEquals(true, byDefault.get("study_filtered"));
        assertEquals(Set.of("STU-50", "STU-53"), refs(byDefault));
        assertEquals("Studying", rows(byDefault).get(0).get("study_status"));

        Map<String, Map<String, Object>> chosen = new LinkedHashMap<>();
        service.checks(List.of("ON_HOLD", "graduated")).forEach(c -> chosen.put((String) c.get("key"), c));
        assertEquals(Set.of("STU-51", "STU-52"), refs(chosen.get("students_without_payment_start")));
        assertEquals(Set.of("STU-51", "STU-52"), refs(chosen.get("students_without_chapter")));

        // checks about the status itself ignore the filter
        assertNull(chosen.get("finished_students_still_mapped").get("study_filtered"));
        assertNull(chosen.get("possible_duplicate_students").get("study_filtered"));
    }

    @Test
    void studyStatusFilterAcceptsCommaListsAndFallsBackToStudying() {
        assertEquals(Set.of("STUDYING"), DataQualityService.studyStatuses(null));
        assertEquals(Set.of("STUDYING"), DataQualityService.studyStatuses(List.of("nonsense", "")));
        assertEquals(Set.of("ON_HOLD", "DROPPED_OUT"), DataQualityService.studyStatuses(List.of("on_hold, DROPPED_OUT")));
    }

    private static Set<Object> refs(Map<String, Object> check) {
        Set<Object> out = new HashSet<>();
        rows(check).forEach(r -> out.add(r.get("user_id")));
        return out;
    }

    @Test
    void sponsorPhoneOtherChapterAndSpentProof() {
        User sp = user(60, 5, "SP-60"); sp.setName("Sara Sponsor"); sp.setChapterId(1L); sp.setPhone("12"); // too short
        User ok = user(61, 5, "SP-61"); ok.setName("Omar Sponsor"); ok.setChapterId(2L); ok.setPhone("+91 98765 43210");
        User st = user(62, 6, "ST-62"); st.setName("Ayesha"); st.setChapterId(2L); st.setStudyStatus("STUDYING");
        when(users.findByRoleId(5)).thenReturn(List.of(sp, ok));
        when(users.findByRoleId(6)).thenReturn(List.of(st));
        var gg = new com.rahbar.entity.GrantorGrantee();
        gg.setGrantorId(60L);
        gg.setGranteeId(62L);
        when(mappings.findAll()).thenReturn(List.of(gg));
        var old = new com.rahbar.entity.Payment();
        old.setPaymentId(1L); old.setGrantorId(60L); old.setGranteeId(62L); old.setStatus("Paid");
        old.setPaymentDate(java.time.LocalDateTime.now().minusDays(45));
        var recent = new com.rahbar.entity.Payment();
        recent.setPaymentId(2L); recent.setGrantorId(60L); recent.setGranteeId(62L); recent.setStatus("Paid");
        recent.setPaymentDate(java.time.LocalDateTime.now().minusDays(5));
        var proved = new com.rahbar.entity.Payment();
        proved.setPaymentId(3L); proved.setGrantorId(60L); proved.setGranteeId(62L); proved.setStatus("Paid");
        proved.setPaymentDate(java.time.LocalDateTime.now().minusDays(90)); proved.setStudentProofUrl("proof.pdf");
        when(payments.findAll()).thenReturn(List.of(old, recent, proved));

        var checks = byKey();
        assertEquals(List.of(60L), rows(checks.get("sponsors_without_phone")).stream().map(r -> r.get("id")).toList());
        var other = rows(checks.get("students_sponsor_other_chapter"));
        assertEquals(1, other.size()); // student in chapter 2, sponsor in chapter 1
        assertEquals(62L, other.get(0).get("id"));
        assertEquals(List.of(1L), rows(checks.get("payments_without_spent_proof")).stream().map(r -> r.get("payment_id")).toList());
    }
}
