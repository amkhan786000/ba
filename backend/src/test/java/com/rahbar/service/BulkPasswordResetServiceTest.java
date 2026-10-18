package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bulk password reset: who gets a new password, who is skipped, and that a failed email changes nothing. */
class BulkPasswordResetServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final BulkPasswordResetService service = new BulkPasswordResetService(users, roles, encoder, email,
            mock(NotificationService.class), mock(TrustedDeviceService.class));

    private void signIn(int role) {
        User me = new User();
        me.setId(1L);
        me.setRoleId(role);
        var p = new RahbarUserPrincipal(me, Set.of("USERS:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private User student(long id, Integer year, String mail, String status) {
        User u = new User();
        u.setId(id); u.setUserId("ST-" + id); u.setName("Student " + id); u.setRoleId(6); u.setYear(year); u.setEmail(mail);
        u.setStatus(status); u.setPasswordHash("old");
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private void setUp() {
        Role st = new Role();
        st.setRoleId(6);
        st.setRoleName("beneficiary");
        when(roles.findById(6)).thenReturn(Optional.of(st));
        when(encoder.encode(anyString())).thenReturn("new-hash");
    }

    @Test
    void onlyTheSuperAdmin() {
        signIn(2);
        assertThrows(ApiException.class, () -> service.preview(Map.of("roleId", 6, "years", List.of(2025))));
    }

    @Test
    void studentsOfTheChosenYearsWithARealEmail() throws Exception {
        signIn(Role.SUPER_ADMIN);
        setUp();
        User ok = student(10, 2025, "ok@example.com", "Active");
        User failsEmail = student(11, 2025, "fail@example.com", "Active");
        User placeholder = student(12, 2025, "ST-12@rahbar.com", "Active");
        User otherYear = student(13, 2024, "other@example.com", "Active");
        User inactive = student(14, 2025, "gone@example.com", "Inactive");
        when(users.findByRoleId(6)).thenReturn(List.of(ok, failsEmail, placeholder, otherYear, inactive));
        when(email.sendWithSecret(eq(EmailType.PASSWORD_RESET_BY_ADMIN), eq("ok@example.com"), anyMap(), anyString())).thenReturn(true);
        when(email.sendWithSecret(eq(EmailType.PASSWORD_RESET_BY_ADMIN), eq("fail@example.com"), anyMap(), anyString())).thenReturn(false);

        Map<String, Object> preview = service.preview(Map.of("roleId", 6, "years", List.of(2025)));
        assertEquals(2, preview.get("to_reset"));
        assertEquals(1, ((List<?>) preview.get("skipped")).size());
        assertThrows(ApiException.class, () -> service.preview(Map.of("roleId", 6))); // a year is needed for students

        service.start(Map.of("roleId", 6, "years", List.of(2025)));
        Map<String, Object> s = service.status();
        for (int i = 0; i < 100 && "running".equals(s.get("state")); i++) { Thread.sleep(20); s = service.status(); }
        assertEquals("finished", s.get("state"));
        assertEquals(1, s.get("reset"));
        assertEquals(1, ((List<?>) s.get("failed")).size());
        assertEquals("new-hash", ok.getPasswordHash());
        assertTrue(ok.getMustChangePassword());
        assertEquals("old", failsEmail.getPasswordHash()); // the email failed: the old password still works
        assertEquals("old", placeholder.getPasswordHash());
        assertEquals(0L, s.get("changed_since"));
    }

    @Test
    void onlyThoseStillOnATemporaryPasswordOrOnlyThoseWithTheirOwn() {
        signIn(Role.SUPER_ADMIN);
        setUp();
        User temp = student(20, 2025, "temp@example.com", "Active");
        temp.setMustChangePassword(true);
        User own = student(21, 2025, "own@example.com", "Active");
        own.setMustChangePassword(false);
        when(users.findByRoleId(6)).thenReturn(List.of(temp, own));
        assertEquals(2, service.preview(Map.of("roleId", 6, "years", List.of(2025), "who", "ALL")).get("to_reset"));
        assertEquals(1, service.preview(Map.of("roleId", 6, "years", List.of(2025), "who", "TEMPORARY")).get("to_reset"));
        assertEquals(1, service.preview(Map.of("roleId", 6, "years", List.of(2025), "who", "OWN")).get("to_reset"));
        assertThrows(ApiException.class, () -> service.preview(Map.of("roleId", 6, "years", List.of(2025), "who", "X")));
    }
}
