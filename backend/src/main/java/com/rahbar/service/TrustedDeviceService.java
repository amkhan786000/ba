package com.rahbar.service;

import com.rahbar.dto.RememberedDevice;
import com.rahbar.entity.TrustedDevice;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.TrustedDeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Remember this device": after the emailed code, a browser gets a random token (in an HttpOnly cookie) that lets
 * the same user skip the code there until it expires. The password is always still needed. Only a hash of the token
 * is stored, so a copy of the database can't be used to sign in.
 */
@Service
public class TrustedDeviceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TrustedDeviceRepository devices;
    private final SignInSettings settings;

    public TrustedDeviceService(TrustedDeviceRepository devices, SignInSettings settings) {
        this.devices = devices;
        this.settings = settings;
    }

    /** Remembers the browser for the configured number of days; empty when the feature is switched off. */
    public Optional<RememberedDevice> remember(Long userId, String userAgent, String ip) {
        int days = settings.rememberDays();
        if (days <= 0 || userId == null) return Optional.empty();
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        TrustedDevice d = new TrustedDevice();
        d.setUserId(userId);
        d.setTokenHash(hash(token));
        d.setDeviceName(deviceName(userAgent));
        d.setIpAddress(ip);
        d.setCreatedAt(LocalDateTime.now());
        d.setLastUsedAt(d.getCreatedAt());
        d.setExpiresAt(d.getCreatedAt().plusDays(days));
        devices.save(d);
        return Optional.of(new RememberedDevice(token, days));
    }

    /** True when the token belongs to this user's remembered, unexpired browser (and the feature is on). */
    public boolean isTrusted(Long userId, String token, String ip) {
        if (token == null || token.isBlank() || userId == null || settings.rememberDays() <= 0) return false;
        Optional<TrustedDevice> found = devices.findByTokenHash(hash(token))
                .filter(d -> d.getUserId().equals(userId) && d.getExpiresAt().isAfter(LocalDateTime.now()));
        found.ifPresent(d -> {
            d.setLastUsedAt(LocalDateTime.now());
            d.setIpAddress(ip);
            devices.save(d);
        });
        return found.isPresent();
    }

    /** The user's remembered devices, newest first, marking the one the request comes from. */
    public List<Map<String, Object>> list(Long userId, String currentToken) {
        String current = currentToken == null || currentToken.isBlank() ? null : hash(currentToken);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TrustedDevice d : devices.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (!d.getExpiresAt().isAfter(now)) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", d.getId());
            r.put("device_name", d.getDeviceName());
            r.put("created_at", d.getCreatedAt());
            r.put("last_used_at", d.getLastUsedAt());
            r.put("expires_at", d.getExpiresAt());
            r.put("current", d.getTokenHash().equals(current));
            rows.add(r);
        }
        return rows;
    }

    public void forget(Long userId, Long id) {
        TrustedDevice d = devices.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Device not found."));
        devices.delete(d);
    }

    /** Forgets every remembered device of the user (password changed or reset, or "Forget all"). */
    public long forgetAll(Long userId) {
        return userId == null ? 0 : devices.deleteByUserId(userId);
    }

    @Scheduled(cron = "${app.trusted-devices.cleanup-cron:0 15 3 * * *}")
    public void removeExpired() {
        devices.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    /** "Chrome on Windows", "Safari on iPhone", ... from a User-Agent header (best effort). */
    static String deviceName(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown browser";
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("SamsungBrowser") ? "Samsung Internet"
                : ua.contains("OPR/") || ua.contains("Opera") ? "Opera"
                : ua.contains("Firefox/") || ua.contains("FxiOS") ? "Firefox"
                : ua.contains("Chrome/") || ua.contains("CriOS") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("iPhone") ? "iPhone"
                : ua.contains("iPad") ? "iPad"
                : ua.contains("Android") ? "Android"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS X") || ua.contains("Macintosh") ? "Mac"
                : ua.contains("CrOS") ? "Chromebook"
                : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " on " + os;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
