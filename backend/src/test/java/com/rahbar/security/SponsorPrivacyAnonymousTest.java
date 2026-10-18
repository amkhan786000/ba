package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** An anonymous sponsor is hidden from everyone but the Super Admin and the sponsor themselves. */
class SponsorPrivacyAnonymousTest {

    private final UserRepository users = mock(UserRepository.class);
    private final SponsorPrivacy privacy = new SponsorPrivacy(users);

    private void signIn(long id, int role, String... permissions) {
        User me = new User();
        me.setId(id);
        me.setUserId("U-" + id);
        me.setRoleId(role);
        var p = new RahbarUserPrincipal(me, Set.of(permissions), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        when(users.findAnonymousSponsorIds()).thenReturn(List.of(60L));
        when(users.findAnonymousSponsorCodes()).thenReturn(List.of("SP-60"));
        when(users.findSponsorIds()).thenReturn(List.of(60L, 61L));
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private static Map<String, Object> sponsorRow(long id, String code, String name) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id); r.put("user_id", code); r.put("name", name); r.put("role_id", 5);
        r.put("email", name.toLowerCase() + "@example.com"); r.put("chapter_name", "Jeddah");
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mask(Object body) { return (List<Map<String, Object>>) privacy.mask(body); }

    @Test
    void staffWithSponsorDetailsStillDontSeeAnAnonymousSponsor() {
        signIn(2, 2, "SPONSOR_DETAILS:VIEW");
        assertTrue(privacy.appliesNow());
        Map<String, Object> installment = new LinkedHashMap<>(Map.of("installment_no", 2, "sponsor_id", 60L, "sponsor_name", "Real Name",
                "sponsor_code", "SP-60", "sponsor_email", "real@example.com"));
        Map<String, Object> report = new LinkedHashMap<>(Map.of("sponsor_id", "SP-60", "sponsor_name", "Real Name", "sponsor_chapter", "Jeddah"));
        var out = mask(List.of(sponsorRow(60, "SP-60", "Real Name"), sponsorRow(61, "SP-61", "Known Person"), installment, report));
        assertEquals(SponsorPrivacy.ANONYMOUS, out.get(0).get("name"));
        assertNull(out.get(0).get("email"));
        assertEquals("SP-60", out.get(0).get("user_id")); // still pickable
        assertEquals(true, out.get(0).get("anonymous"));
        assertEquals("Known Person", out.get(1).get("name")); // an ordinary sponsor, with details for this user
        assertEquals("known person@example.com", out.get(1).get("email"));
        assertEquals(SponsorPrivacy.ANONYMOUS, out.get(2).get("sponsor_name"));
        assertNull(out.get(2).get("sponsor_email"));
        assertEquals(SponsorPrivacy.ANONYMOUS, out.get(3).get("sponsor_name"));
        assertNull(out.get(3).get("sponsor_chapter"));
    }

    @Test
    void theSuperAdminAndTheSponsorThemselvesSeeTheName() {
        signIn(1, Role.SUPER_ADMIN, "SPONSOR_DETAILS:VIEW");
        assertFalse(privacy.appliesNow());
        signIn(60, 5);
        var out = mask(List.of(sponsorRow(60, "SP-60", "Real Name")));
        assertEquals("Real Name", out.get(0).get("name"));
    }

    @Test
    void aStudentIsToldTheChapter() {
        User sp = new User(); sp.setId(60L); sp.setName("Real Name"); sp.setAnonymousSponsor(true);
        User student = mock(User.class);
        when(student.getChapterName()).thenReturn("Jeddah Chapter");
        assertEquals("A sponsor from Jeddah Chapter", SponsorPrivacy.forStudent(sp, student));
        sp.setAnonymousSponsor(false);
        assertEquals("Real Name", SponsorPrivacy.forStudent(sp, student));
    }
}
