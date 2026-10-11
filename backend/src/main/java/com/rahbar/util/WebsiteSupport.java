package com.rahbar.util;

import com.rahbar.util.WebsiteSupport;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Small helpers shared by the website controllers. */
public final class WebsiteSupport {

    private WebsiteSupport() {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    public static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public static boolean present(String q) {
        return q != null && !q.isBlank();
    }

    /** Case-insensitive "contains" across the fields; % and _ typed by the user match literally. */
    public static <T> Predicate anyFieldContains(Root<T> root, CriteriaBuilder cb, String q, String... fields) {
        String like = "%" + q.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return cb.or(Arrays.stream(fields).map(f -> cb.like(cb.lower(root.get(f)), like, '\\')).toArray(Predicate[]::new));
    }

    public static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found.");
    }

    public static void require(Section section, Section.Level level) {
        if (!Access.can(section, level)) throw Access.forbidden("You do not have permission for this.");
    }

    public static boolean can(Section section, Section.Level level) {
        return Access.can(section, level);
    }

    public static User me() {
        return Access.current().getUser();
    }

    /** The user's chapter when their role only covers their own chapter, else null. */
    public static Long scopedChapter() {
        return Access.chapterScoped() ? orNone(me().getChapterId()) : null;
    }

    /** The user's RCC center when their role only covers their own center, else null. */
    public static Long scopedCenter() {
        return Access.rccScoped() ? orNone(me().getRccCenterId()) : null;
    }

    /** A scoped user without a chapter / center sees nothing rather than everything. */
    private static Long orNone(Long id) {
        return id == null ? -1L : id;
    }

    public static String text(String v, String label, int max, boolean required) {
        String s = v == null || v.isBlank() ? null : v.strip();
        if (s == null && required) throw bad(label + " is required.");
        if (s != null && s.length() > max) throw bad(label + " is too long (at most " + max + " characters).");
        return s;
    }

    public static String email(String v, String label, boolean required) {
        String s = text(v, label, 160, required);
        if (s != null && !EMAIL.matcher(s).matches()) throw bad("Please enter a valid email address.");
        return s;
    }

    public static String slug(String v) {
        String s = text(v, "Slug", 200, true).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(s).matches()) throw bad("Slug: use lower-case letters, numbers and dashes.");
        return s;
    }

    public static String slugify(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) s = "post";
        return s.length() > 150 ? s.substring(0, 150) : s;
    }

    /** The visitor's address: the website's nginx passes it in X-Forwarded-For. */
    public static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].strip();
        return request.getRemoteAddr();
    }
}
