package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Merging a person's second account (e.g. a separate sponsor account) into their main one. */
class PersonMergeServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdditionalRoleService additionalRoles = mock(AdditionalRoleService.class);
    private final PaymentInstallmentService installments = mock(PaymentInstallmentService.class);
    private final PersonMergeService service = new PersonMergeService(users, roles, jdbc, additionalRoles, installments);

    private final User admin = user(1L, "A1", 2, "admin@ba.org", "9000000001");
    private final User sponsor = user(2L, "S2", 5, "me@gmail.com", null);

    private static User user(Long id, String code, int role, String email, String phone) {
        User u = new User();
        u.setId(id);
        u.setUserId(code);
        u.setName("Amjad Khan");
        u.setRoleId(role);
        u.setEmail(email);
        u.setPhone(phone);
        u.setStatus("Active");
        return u;
    }

    private static void signIn(int roleId) {
        User u = user(99L, "ME", roleId, null, null);
        var p = new RahbarUserPrincipal(u, Set.of(), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void found() {
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(sponsor));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(additionalRoles.of(anyLong())).thenReturn(List.of());
    }

    @Test
    void theKeptAccountGetsTheOtherRolesAndEverythingMoves() {
        signIn(Role.SUPER_ADMIN);
        found();
        Map<String, Object> res = service.merge(1L, 2L);

        verify(jdbc).update(contains("UPDATE grantor_grantees SET grantor_id = ?"), eq(1L), eq(2L));
        verify(jdbc).update(contains("UPDATE payments SET grantor_id = ?"), eq(1L), eq(2L));
        verify(jdbc).update(contains("UPDATE reb_sponsorships SET sponsor_user_id = ?"), eq(1L), eq(2L));
        verify(additionalRoles).set(eq(admin), argThat(r -> r.containsAll(List.of(2, 5))));
        verify(additionalRoles).set(eq(sponsor), eq(List.of()));
        assertEquals("Inactive", sponsor.getStatus());
        assertEquals(1L, sponsor.getMergedIntoId());
        assertEquals("admin@ba.org", admin.getEmail()); // the kept account's email stays
        verify(installments, never()).sync(anyLong()); // no student involved
        assertTrue(String.valueOf(res.get("message")).contains("S2 was merged into A1"));
    }

    @Test
    void onlyTheSuperAdminMerges() {
        signIn(2);
        found();
        assertThrows(ApiException.class, () -> service.merge(1L, 2L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void twoStudentAccountsGoToTheStudentMerge() {
        signIn(Role.SUPER_ADMIN);
        User s1 = user(3L, "ST3", 6, null, null), s2 = user(4L, "ST4", 6, null, null);
        when(users.findById(3L)).thenReturn(Optional.of(s1));
        when(users.findById(4L)).thenReturn(Optional.of(s2));
        ApiException e = assertThrows(ApiException.class, () -> service.merge(3L, 4L));
        assertTrue(e.getMessage().contains("student merge"));
        assertThrows(ApiException.class, () -> service.merge(1L, 1L));
    }
}
