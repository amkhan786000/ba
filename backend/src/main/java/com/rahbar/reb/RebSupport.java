package com.rahbar.reb;

import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Reading and checking form values for the REB screens. */
final class RebSupport {

    private RebSupport() {}

    static final Set<String> TRACKS = Set.of("MADRASA", "SCHOOL", "BOTH");

    static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found.");
    }

    /** Trimmed text, null when blank; 400 when required and missing or longer than max. */
    static String text(Object v, String label, int max, boolean required) {
        String s = v == null ? null : String.valueOf(v).strip();
        if (s != null && s.isEmpty()) s = null;
        if (s == null && required) throw bad(label + " is required.");
        if (s != null && s.length() > max) throw bad(label + " is too long (at most " + max + " characters).");
        return s;
    }

    static String text(Map<String, Object> body, String key, String label, int max) {
        return text(body.get(key), label, max, false);
    }

    /** One of the allowed codes (upper-cased); the default when blank. */
    static String code(Object v, Set<String> allowed, String fallback, String label) {
        String s = v == null ? "" : String.valueOf(v).strip().toUpperCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return fallback;
        if (!allowed.contains(s)) throw bad("Unknown " + label + ": " + v + ".");
        return s;
    }

    static LocalDate date(Object v, String label) {
        String s = v == null ? "" : String.valueOf(v).strip();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            throw bad(label + " must be a date like 2026-07-01.");
        }
    }

    static Long id(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(v).strip());
        } catch (NumberFormatException e) {
            throw bad("Invalid id: " + v + ".");
        }
    }

    static Integer number(Object v, String label, int min, int max) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        int n;
        try {
            n = new BigDecimal(String.valueOf(v).strip()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw bad(label + " must be a whole number.");
        }
        if (n < min || n > max) throw bad(label + " must be between " + min + " and " + max + ".");
        return n;
    }

    static BigDecimal money(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            BigDecimal b = new BigDecimal(String.valueOf(v).strip().replace(",", ""));
            if (b.signum() < 0) throw bad(label + " cannot be negative.");
            return b;
        } catch (NumberFormatException e) {
            throw bad(label + " must be a number.");
        }
    }

    static boolean flag(Object v, boolean fallback) {
        if (v == null || String.valueOf(v).isBlank()) return fallback;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).strip().toLowerCase(java.util.Locale.ROOT);
        return s.equals("true") || s.equals("yes") || s.equals("y") || s.equals("1");
    }

    /** A 10-digit Indian mobile number, or what was typed when it has another form (kept as typed). */
    static String phone(Object v, String label) {
        return text(v, label, 20, false);
    }
}
