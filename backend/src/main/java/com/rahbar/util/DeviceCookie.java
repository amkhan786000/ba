package com.rahbar.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The "remember this device" cookie: HttpOnly (page scripts can't read it), SameSite=Strict, only sent to /api, and
 * Secure whenever the site is opened over HTTPS (directly or behind Caddy / nginx).
 */
public final class DeviceCookie {

    public static final String NAME = "rahbar_device";

    private DeviceCookie() {}

    public static String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, String token, int days) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, token, Duration.ofDays(days)).toString());
    }

    public static void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, "", Duration.ZERO).toString());
    }

    private static ResponseCookie build(HttpServletRequest request, String value, Duration maxAge) {
        boolean https = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(https).sameSite("Strict").path("/api").maxAge(maxAge).build();
    }
}
