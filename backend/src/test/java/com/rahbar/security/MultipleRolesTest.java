package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** One person, several roles: permissions together, the widest admin scope, REB class limits kept apart. */
class MultipleRolesTest {

    private static Role role(int id, String scope, String permissions) {
        Role r = new Role();
        r.setRoleId(id);
        r.setScope(scope);
        r.setPermissions(permissions);
        return r;
    }

    private static final Role SPONSOR = role(5, "ALL", "");
    private static final Role OFFICE = role(8, "ALL", "STUDENTS:VIEW,PAYMENT_RECORDS:VIEW");
    private static final Role CHAPTER_LEAD = role(9, "CHAPTER", "STUDENTS:VIEW,CHAPTER_DASHBOARD:VIEW");
    private static final Role RCC_LEAD = role(10, "RCC", "RCC_STUDENTS:EDIT");
    private static final Role TEACHER = role(11, "REB_CLASSES", "REB_ATTENDANCE:EDIT");
    private static final Role REB_OFFICE = role(12, "ALL", "REB_STUDENTS:VIEW");

    private static User user(int mainRole) {
        User u = new User();
        u.setId(1L);
        u.setRoleId(mainRole);
        return u;
    }

    private static RahbarUserPrincipal of(Role... roles) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Role r : roles) ids.add(r.getRoleId());
        return RahbarUserDetailsService.combine(user(roles[0].getRoleId()), ids, List.of(roles));
    }

    @Test
    void oneRoleKeepsExactlyItsPermissionsAndScope() {
        RahbarUserPrincipal p = of(CHAPTER_LEAD);
        assertEquals("CHAPTER", p.getScope());
        assertTrue(p.getPermissions().contains("STUDENTS:VIEW"));
        assertTrue(of(TEACHER).isRebClassesOnly());
        assertEquals("REB_CLASSES", of(TEACHER).getScope());
        assertEquals(Set.of("ROLE_9"), Set.copyOf(of(CHAPTER_LEAD).getAuthorities().stream().map(Object::toString).filter(a -> a.startsWith("ROLE_")).toList()));
    }

    @Test
    void superAdminWhoIsAlsoASponsorGetsEverythingAndBothPortals() {
        Role superAdmin = role(Role.SUPER_ADMIN, "ALL", "");
        RahbarUserPrincipal p = of(superAdmin, SPONSOR);
        assertEquals(Section.allKeys(), p.getPermissions());
        assertTrue(p.hasRole(Role.SUPER_ADMIN));
        assertTrue(p.hasRole(5));
        assertTrue(p.getAuthorities().stream().map(Object::toString).toList().containsAll(List.of("ROLE_1", "ROLE_5")));
    }

    @Test
    void aSponsorRoleDoesNotWidenAChapterLead() {
        RahbarUserPrincipal p = of(CHAPTER_LEAD, SPONSOR);
        assertEquals("CHAPTER", p.getScope());
        assertTrue(p.hasRole(5));
    }

    @Test
    void permissionsAddUpAndTheWidestAdminScopeWins() {
        RahbarUserPrincipal p = of(CHAPTER_LEAD, OFFICE);
        assertEquals("ALL", p.getScope());
        assertTrue(p.getPermissions().containsAll(Set.of("STUDENTS:VIEW", "CHAPTER_DASHBOARD:VIEW", "PAYMENT_RECORDS:VIEW")));
        assertEquals("CHAPTER", of(RCC_LEAD, CHAPTER_LEAD).getScope());
    }

    @Test
    void aTeacherWhoIsAlsoAChapterLeadKeepsBothLimits() {
        RahbarUserPrincipal p = of(TEACHER, CHAPTER_LEAD);
        assertEquals("CHAPTER", p.getScope());
        assertTrue(p.isRebClassesOnly());
        assertFalse(of(TEACHER, REB_OFFICE).isRebClassesOnly()); // another role sees all of REB
    }
}
