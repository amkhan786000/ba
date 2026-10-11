package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Error alerts: grouped, at most one email every 15 minutes, to ALERT_EMAILS or the Super Admins. */
class ErrorAlertServiceTest {

    private final EmailService email = mock(EmailService.class);
    private final UserRepository users = mock(UserRepository.class);

    private static User admin(String email, String status) {
        User u = new User();
        u.setEmail(email);
        u.setStatus(status);
        u.setRoleId(Role.SUPER_ADMIN);
        return u;
    }

    @Test
    void theSameErrorIsGroupedAndEmailedOnce() {
        ErrorAlertService s = new ErrorAlertService(email, users, "ops@example.com, second@example.com");
        when(email.send(anyString(), anyString(), anyString())).thenReturn(true);
        for (int i = 0; i < 3; i++) s.record(new IllegalStateException("boom"), "GET /api/admin/students");
        s.record(new NullPointerException(), "Background job");

        assertTrue(s.sendPending(Instant.now()));
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class), body = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq("ops@example.com"), subject.capture(), body.capture());
        verify(email).send(eq("second@example.com"), anyString(), anyString());
        assertEquals("Rahbar: 4 unexpected errors", subject.getValue());
        assertTrue(body.getValue().contains("GET /api/admin/students - 3 times"));
        assertTrue(body.getValue().contains("Background job - 1 time"));
        assertTrue(body.getValue().contains("IllegalStateException: boom"));
    }

    @Test
    void atMostOneEmailEvery15MinutesAndNoneWithoutErrors() {
        ErrorAlertService s = new ErrorAlertService(email, users, "ops@example.com");
        when(email.send(anyString(), anyString(), anyString())).thenReturn(true);
        Instant t = Instant.parse("2026-10-11T10:00:00Z");
        assertFalse(s.sendPending(t)); // nothing happened
        s.record(new RuntimeException("a"), "GET /x");
        assertTrue(s.sendPending(t));
        s.record(new RuntimeException("b"), "GET /y");
        assertFalse(s.sendPending(t.plusSeconds(600)));  // 10 minutes later: wait
        assertTrue(s.sendPending(t.plusSeconds(900)));   // 15 minutes later: the waiting error goes out
        verify(email, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void withoutAlertEmailsTheActiveSuperAdminsGetIt() {
        when(users.findByRoleId(Role.SUPER_ADMIN)).thenReturn(List.of(admin("boss@example.com", "Active"),
                admin("old@example.com", "Inactive"), admin("1001@rahbar.com", "Active"), admin(null, "Active")));
        ErrorAlertService s = new ErrorAlertService(email, users, "");
        assertEquals(List.of("boss@example.com"), s.recipients());
    }

    @Test
    void referencesAreShortAndReadable() {
        String ref = ErrorAlertService.newRef();
        assertTrue(ref.matches("[A-Z2-9]{6}"), ref);
        assertFalse(ref.matches(".*[01IO].*"));
    }
}
