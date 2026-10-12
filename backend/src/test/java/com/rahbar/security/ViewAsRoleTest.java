package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.entity.UserAdditionalRole;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserAdditionalRoleRepository;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** "View as": a person with several roles can narrow a request to exactly one of them. */
class ViewAsRoleTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserAdditionalRoleRepository additional = mock(UserAdditionalRoleRepository.class);
    private final RahbarUserDetailsService service = new RahbarUserDetailsService(users, roles, additional);

    private static Role role(int id, String scope, String permissions) {
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleName("Role " + id);
        r.setScope(scope);
        r.setPermissions(permissions);
        return r;
    }

    private static UserAdditionalRole extra(int roleId) {
        UserAdditionalRole r = new UserAdditionalRole();
        r.setUserId(1L);
        r.setRoleId(roleId);
        return r;
    }

    private User me() {
        User u = new User();
        u.setId(1L);
        u.setRoleId(Role.SUPER_ADMIN);
        u.setChapterId(4L);
        when(roles.findById(1)).thenReturn(Optional.of(role(1, "ALL", "")));
        when(roles.findById(5)).thenReturn(Optional.of(role(5, "ALL", "")));
        when(roles.findById(9)).thenReturn(Optional.of(role(9, "CHAPTER", "STUDENTS:VIEW,CHAPTER_DASHBOARD:VIEW")));
        when(additional.findByUserId(1L)).thenReturn(List.of(extra(5), extra(9))); // Super Admin + Sponsor + Chapter Lead
        return u;
    }

    @Test
    void viewingAsChapterLeadGivesExactlyThatRole() {
        RahbarUserPrincipal p = service.principalFor(me(), 9);
        assertEquals(Set.of("STUDENTS:VIEW", "CHAPTER_DASHBOARD:VIEW"), p.getPermissions());
        assertEquals("CHAPTER", p.getScope());
        assertEquals(Set.of(9), p.getRoleIds());
        assertFalse(p.hasRole(Role.SUPER_ADMIN));
        assertEquals(9, p.getActingRoleId());
        assertEquals(Set.of(1, 5, 9), p.getAllRoleIds());
        assertTrue(p.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_1")));
    }

    @Test
    void withoutViewAsOrWithARoleTheyDontHaveItIsAllTheirRoles() {
        for (Integer acting : Arrays.asList(null, 7)) {
            RahbarUserPrincipal p = service.principalFor(me(), acting);
            assertEquals(Section.allKeys(), p.getPermissions());
            assertEquals(Set.of(1, 5, 9), p.getRoleIds());
            assertNull(p.getActingRoleId());
        }
    }
}
