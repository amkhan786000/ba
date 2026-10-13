package com.rahbar.service;

import com.rahbar.dto.RememberedDevice;
import com.rahbar.entity.TrustedDevice;
import com.rahbar.repository.TrustedDeviceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TrustedDeviceServiceTest {

    private final TrustedDeviceRepository repo = mock(TrustedDeviceRepository.class);
    private final SignInSettings settings = mock(SignInSettings.class);
    private final TrustedDeviceService service = new TrustedDeviceService(repo, settings);

    private TrustedDevice stored(Long userId, String token, LocalDateTime expires) {
        TrustedDevice d = new TrustedDevice();
        d.setUserId(userId);
        d.setTokenHash(TrustedDeviceService.hash(token));
        d.setExpiresAt(expires);
        return d;
    }

    @Test
    void remembersForTheConfiguredDaysAndStoresOnlyAHash() {
        when(settings.rememberDays()).thenReturn(30);
        RememberedDevice r = service.remember(7L, "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36", "1.2.3.4").orElseThrow();
        assertEquals(30, r.days());
        assertTrue(r.token().length() >= 40);
        ArgumentCaptor<TrustedDevice> saved = ArgumentCaptor.forClass(TrustedDevice.class);
        verify(repo).save(saved.capture());
        assertEquals(TrustedDeviceService.hash(r.token()), saved.getValue().getTokenHash());
        assertNotEquals(r.token(), saved.getValue().getTokenHash());
        assertEquals("Chrome on Windows", saved.getValue().getDeviceName());
        assertEquals(saved.getValue().getCreatedAt().plusDays(30), saved.getValue().getExpiresAt());
    }

    @Test
    void nothingIsRememberedWhenSwitchedOff() {
        when(settings.rememberDays()).thenReturn(0);
        assertTrue(service.remember(7L, "ua", "ip").isEmpty());
        verify(repo, never()).save(any());
    }

    @Test
    void onlyTheSameUsersUnexpiredDeviceIsTrusted() {
        when(settings.rememberDays()).thenReturn(30);
        when(repo.findByTokenHash(TrustedDeviceService.hash("good"))).thenReturn(Optional.of(stored(7L, "good", LocalDateTime.now().plusDays(3))));
        when(repo.findByTokenHash(TrustedDeviceService.hash("old"))).thenReturn(Optional.of(stored(7L, "old", LocalDateTime.now().minusMinutes(1))));
        when(repo.findByTokenHash(TrustedDeviceService.hash("unknown"))).thenReturn(Optional.empty());

        assertTrue(service.isTrusted(7L, "good", "ip"));
        assertFalse(service.isTrusted(8L, "good", "ip"), "another user's device");
        assertFalse(service.isTrusted(7L, "old", "ip"), "expired");
        assertFalse(service.isTrusted(7L, "unknown", "ip"));
        assertFalse(service.isTrusted(7L, null, "ip"));
        assertFalse(service.isTrusted(7L, "", "ip"));
    }

    @Test
    void switchingOffStopsRememberedDevicesSkippingTheCode() {
        when(settings.rememberDays()).thenReturn(0);
        assertFalse(service.isTrusted(7L, "good", "ip"));
        verify(repo, never()).findByTokenHash(anyString());
    }

    @Test
    void deviceNamesAreReadable() {
        assertEquals("Safari on iPhone", TrustedDeviceService.deviceName(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"));
        assertEquals("Chrome on Android", TrustedDeviceService.deviceName(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"));
        assertEquals("Edge on Windows", TrustedDeviceService.deviceName(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0"));
        assertEquals("Firefox on Mac", TrustedDeviceService.deviceName(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.0; rv:121.0) Gecko/20100101 Firefox/121.0"));
        assertEquals("Unknown browser", TrustedDeviceService.deviceName(null));
    }
}
