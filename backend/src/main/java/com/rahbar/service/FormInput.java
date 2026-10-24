package com.rahbar.service;

import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/** Reading request fields with friendly 400 messages (careers, events, helpline). */
final class FormInput {

    private FormInput() {}

    static String text(Object v, String label, int max, boolean required) {
        String s = v == null ? null : String.valueOf(v).trim();
        if (s == null || s.isEmpty()) {
            if (required) throw bad(label + " is required.");
            return null;
        }
        if (s.length() > max) throw bad(label + " can be at most " + max + " characters.");
        return s;
    }

    /** One of the allowed codes (case-insensitive); fallback when empty (null fallback: required). */
    static String code(Object v, Set<String> allowed, String fallback, String label) {
        String c = v == null ? "" : String.valueOf(v).trim().toUpperCase(Locale.ROOT);
        if (c.isEmpty() && fallback != null) return fallback;
        if (!allowed.contains(c)) throw bad(label + ": choose one from the list.");
        return c;
    }

    static LocalDate date(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (Exception e) {
            throw bad(label + " must be a date like 2026-07-01.");
        }
    }

    /** "2026-07-01T10:30" (seconds optional). */
    static LocalDateTime dateTime(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return LocalDateTime.parse(String.valueOf(v).trim());
        } catch (Exception e) {
            throw bad(label + " must be a date and time like 2026-07-01T10:30.");
        }
    }

    static boolean flag(Object v) {
        return v != null && (Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v)));
    }

    static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found.");
    }
}
