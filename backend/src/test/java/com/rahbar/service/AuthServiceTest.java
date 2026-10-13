package com.rahbar.service;

import com.rahbar.dto.LoginRequest;
import com.rahbar.dto.RegisterRequest;
import com.rahbar.dto.ResetPasswordRequest;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.OtpRepository;
import com.rahbar.repository.PasswordResetCodeRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.JwtService;
import com.rahbar.security.RahbarUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A blank email or phone must find nobody. The database would otherwise return every user without an email
 * (hundreds), and the lookup failed with a NonUniqueResultException: a 500 and an alert email.
 */
class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final OtpRepository otps = mock(OtpRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final RahbarUserDetailsService details = mock(RahbarUserDetailsService.class);
    private final TrustedDeviceService devices = mock(TrustedDeviceService.class);
    private final SignInSettings signIn = mock(SignInSettings.class);
    private final AuthService service = new AuthService(users, otps, encoder,
            mock(JwtService.class), email, mock(ActivityLogService.class),
            mock(NotificationService.class), details, mock(PasswordResetCodeRepository.class), devices, signIn);

    private static LoginRequest login(String method, String identifier, String password) {
        LoginRequest r = new LoginRequest();
        r.setLoginMethod(method);
        r.setIdentifier(identifier);
        r.setPassword(password);
        return r;
    }

    @Test
    void blankSignInIsAnInvalidLoginNotAServerError() {
        for (LoginRequest r : new LoginRequest[] { login(null, null, null), login("email", "", "x"),
                login("email", "   ", "x"), login("phone", null, "x") }) {
            ApiException e = assertThrows(ApiException.class, () -> service.login(r, "1.2.3.4"));
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
            assertFalse(e.getMessage().contains("null"), e.getMessage());
        }
        verify(users, never()).findByEmail(any());
        verify(users, never()).findByPhone(any());
    }

    @Test
    void emailIsTrimmedBeforeTheLookup() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.login(login("email", "  a@b.org ", "x"), "1.2.3.4"));
        verify(users).findByEmail("a@b.org");
    }

    @Test
    void missingPasswordIsAWrongPassword() {
        User u = new User();
        u.setId(7L);
        u.setStatus("Active");
        u.setPasswordHash("hash");
        when(users.findByEmail("a@b.org")).thenReturn(Optional.of(u));
        ApiException e = assertThrows(ApiException.class, () -> service.login(login("email", "a@b.org", null), "1.2.3.4"));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    void registerWithoutEmailIsABadRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(" ");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.register(r)).getStatus());
        verify(users, never()).findByEmail(any());
    }

    @Test
    void passwordResetWithoutEmailIsTheUsualWrongCodeAnswer() {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setEmail("");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.resetPassword(r, "1.2.3.4")).getStatus());
        verify(users, never()).findByEmail(any());
    }

    private User activeUser() {
        User u = new User();
        u.setId(7L);
        u.setName("Asha");
        u.setRoleId(5);
        u.setStatus("Active");
        u.setPasswordHash("hash");
        when(users.findByEmail("a@b.org")).thenReturn(Optional.of(u));
        when(encoder.matches("pw", "hash")).thenReturn(true);
        when(details.principalFor(u)).thenReturn(mock(com.rahbar.security.RahbarUserPrincipal.class));
        return u;
    }

    @Test
    void aRememberedDeviceSkipsTheEmailedCode() {
        activeUser();
        when(devices.isTrusted(7L, "token", "1.2.3.4")).thenReturn(true);
        var res = service.login(login("email", "a@b.org", "pw"), "1.2.3.4", "token");
        assertFalse(res.isOtpRequired());
        verify(otps, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void otherwiseACodeIsSentAndRememberingIsOffered() {
        activeUser();
        when(signIn.rememberDays()).thenReturn(30);
        var res = service.login(login("email", "a@b.org", "pw"), "1.2.3.4", "someone-elses-token");
        assertTrue(res.isOtpRequired());
        assertEquals(30, res.getRememberDays());
        verify(otps).save(any());
    }

    @Test
    void aRememberedDeviceStillNeedsThePassword() {
        activeUser();
        when(devices.isTrusted(anyLong(), any(), any())).thenReturn(true);
        assertThrows(ApiException.class, () -> service.login(login("email", "a@b.org", "wrong"), "1.2.3.4", "token"));
    }
}
