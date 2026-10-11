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
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import java.beans.PropertyDescriptor;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
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

    /**
     * The list of one kind of website record. Without page / size: every record ({ data, names }), as before.
     * With them: { total, page, size, rows, names } for one page, after keeping the records where every word of q
     * appears in one of their text / number / date fields. suggest (comma-separated field names) adds the distinct
     * values of those fields over all the records, for the form's suggestions.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Map<String, Object> list(List<T> records, String q, Integer page, Integer size, String suggest,
                                                         Function<List<T>, Map<Long, String>> names) {
        if (page == null && size == null) return Map.of("data", records, "names", names.apply(records));
        List<T> matching = q == null || q.isBlank() ? records
                : records.stream().filter(r -> Paging.matches(q, searchable(r).toArray())).toList();
        // The records go out as entities (same JSON as the full list); only the page is kept.
        Map<String, Object> body = Paging.of(matching, page, size, slice -> (List) slice);
        body.put("names", names.apply((List<T>) body.get("rows")));
        if (suggest != null && !suggest.isBlank()) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (String field : suggest.split(",")) {
                TreeSet<String> distinct = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                for (T r : records) {
                    BeanWrapperImpl w = new BeanWrapperImpl(r);
                    if (w.isReadableProperty(field.strip()) && w.getPropertyValue(field.strip()) instanceof String v && !v.isBlank()) distinct.add(v.strip());
                }
                values.put(field.strip(), new ArrayList<>(distinct));
            }
            body.put("suggestions", values);
        }
        return body;
    }

    /** Text, number, date and enum fields of a record (what the list's search looks in). */
    private static List<Object> searchable(Object record) {
        List<Object> out = new ArrayList<>();
        BeanWrapperImpl w = new BeanWrapperImpl(record);
        for (PropertyDescriptor d : w.getPropertyDescriptors()) {
            if (d.getReadMethod() == null || "class".equals(d.getName())) continue;
            Class<?> t = d.getPropertyType();
            if (t == String.class || Number.class.isAssignableFrom(t) || t.isEnum() || Temporal.class.isAssignableFrom(t)) {
                Object v = w.getPropertyValue(d.getName());
                if (v != null) out.add(v);
            }
        }
        return out;
    }
}
