package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.entity.UserAdditionalRole;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserAdditionalRoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.UserRoles;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Several roles per person: checking a role (main or additional) and setting the additional ones. */
class AdditionalRoleServiceTest {

    private final UserAdditionalRoleRepository additional = mock(UserAdditionalRoleRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AdditionalRoleService service = new AdditionalRoleService(additional, roles);

    private static User user(long id, int mainRole) {
        User u = new User();
        u.setId(id);
        u.setRoleId(mainRole);
        return u;
    }

    private static UserAdditionalRole extra(long userId, int roleId) {
        UserAdditionalRole r = new UserAdditionalRole();
        r.setUserId(userId);
        r.setRoleId(roleId);
        return r;
    }

    @Test
    void aRoleCountsWhetherItIsTheMainOrAnAdditionalOne() {
        UserRepository users = mock(UserRepository.class);
        User admin = user(1, 2);
        when(users.hasAdditionalRole(eq(1L), argThat(c -> c.contains(5)))).thenReturn(true);
        assertTrue(UserRoles.has(admin, 2, users));        // main role, no query needed
        assertTrue(UserRoles.has(admin, 5, users));        // additional
        assertFalse(UserRoles.has(admin, 6, users));
        assertFalse(UserRoles.has(null, 5, users));
        when(users.allAdditionalRoles()).thenReturn(List.<Object[]>of(new Object[]{1L, 5}, new Object[]{99L, 6}));
        assertEquals(Map.of(1L, Set.of(2, 5)), UserRoles.byUser(List.of(admin), users));
    }

    @Test
    void settingRolesAddsAndRemovesAndNeverRepeatsTheMainRole() {
        when(roles.existsById(anyInt())).thenReturn(true);
        User u = user(7, 2);
        UserAdditionalRole oldOne = extra(7, 9);
        when(additional.findByUserId(7L)).thenReturn(List.of(oldOne));

        service.set(u, List.of(5, "2", 5, ""));

        verify(additional).delete(oldOne);
        verify(additional).save(argThat(r -> r.getUserId() == 7L && r.getRoleId() == 5));
        verify(additional, times(1)).save(any());
    }

    @Test
    void unknownRolesAreRefused() {
        when(roles.existsById(42)).thenReturn(false);
        assertThrows(ApiException.class, () -> service.set(user(7, 2), List.of(42)));
        assertThrows(ApiException.class, () -> service.set(user(7, 2), List.of("sponsor")));
    }
}
