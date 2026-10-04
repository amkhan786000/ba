package com.rahbar.service;

import com.rahbar.config.AuditConfig;
import com.rahbar.entity.ApplicationStatus;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Small helpers shared by the role services. */
final class ServiceSupport {
    private ServiceSupport() {}

    /** Default "unassigned" grantor: students of a deactivated sponsor are parked on user 12. */
    static final String UNASSIGNED_GRANTOR = "12";
    static final List<Integer> SPONSOR_ROLES = List.of(3, 4, 5);
    static final List<Integer> SPONSOR_CONVENOR_ROLES = List.of(4, 5);
    static final int STUDENT_ROLE = 6;

    /** user_id of the logged-in user (null for public requests). */
    static String me() {
        return AuditConfig.currentUserId();
    }

    static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    static boolean isBlank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    static Long toLong(Object v) {
        return isBlank(v) ? null : Long.valueOf(String.valueOf(v).trim());
    }

    static Integer toInteger(Object v) {
        return isBlank(v) ? null : Integer.valueOf(String.valueOf(v).trim());
    }

    /** Accepts "yyyy-MM-dd", "yyyy-MM-ddTHH:mm[:ss]" or "yyyy-MM-dd HH:mm:ss". */
    static LocalDateTime parseDateTime(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        try {
            if (s.length() == 10) return LocalDate.parse(s).atStartOfDay();
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid date: " + v);
        }
    }

    static User requireUser(UserRepository users, String userId, String notFoundMessage) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, notFoundMessage));
    }

    /** user_id -> name for the given ids (unknown ids are simply absent). */
    static Map<String, String> userNames(UserRepository users, Collection<String> ids) {
        Set<String> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) return Map.of();
        Map<String, String> names = new HashMap<>();
        for (User u : users.findAllById(wanted)) names.put(u.getUserId(), u.getName());
        return names;
    }

    /** Latest status row per application id (ties on created_at go to the highest status_id). */
    static Map<Long, ApplicationStatus> latestByApplication(List<ApplicationStatus> latestRows) {
        Map<Long, ApplicationStatus> result = new HashMap<>();
        for (ApplicationStatus s : latestRows) {
            result.merge(s.getGranteeDetailId(), s,
                    (a, b) -> a.getStatusId() != null && b.getStatusId() != null && a.getStatusId() > b.getStatusId() ? a : b);
        }
        return result;
    }

    /** Chart rows {label, value} counting each label, biggest first. */
    static <T> List<Map<String, Object>> countBy(Collection<T> items, Function<T, String> label) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T item : items) counts.merge(label.apply(item), 1L, Long::sum);
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", e.getKey());
                    row.put("value", e.getValue());
                    rows.add(row);
                });
        return rows;
    }

    /** Region label for charts: blank regions are grouped as "Not set". */
    static String regionLabel(String region) {
        return region == null || region.trim().isEmpty() ? "Not set" : region.trim();
    }

    /** Distinct non-null years, newest first. */
    @SafeVarargs
    static List<Integer> yearsDesc(List<Integer>... lists) {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        for (List<Integer> l : lists) l.stream().filter(Objects::nonNull).forEach(years::add);
        return new ArrayList<>(years);
    }

    /** Case-insensitive "contains", like MySQL's LIKE '%x%' on the default collation. */
    static boolean like(Object value, String needle) {
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    /** Null-safe comparator over row values (nulls first, numbers/dates/strings by natural order). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Comparator<Map<String, Object>> byColumn(String column, boolean descending) {
        Comparator<Object> values = (a, b) -> {
            if (a == b) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            if (a instanceof String sa && b instanceof String sb) return sa.compareToIgnoreCase(sb);
            if (a instanceof Comparable ca && a.getClass().isInstance(b)) return ca.compareTo(b);
            return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
        };
        Comparator<Map<String, Object>> c = (r1, r2) -> values.compare(r1.get(column), r2.get(column));
        return descending ? c.reversed() : c;
    }

    /** Numeric value of a marks string; null when missing or not a number. */
    static Double marks(String marks) {
        if (marks == null || marks.isBlank()) return null;
        try {
            return Double.valueOf(marks.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
