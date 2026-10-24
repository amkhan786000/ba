package com.rahbar.service;

import java.util.*;

/**
 * Admin > Student Directory: the search box and the filters, applied to the directory rows.
 * <p>
 * Search: every word must match. A word matches when it is part of the Ref ID, name, email, phone, sponsor ID or
 * name, chapter, institution or course, or when it is a whole word of the study status label ("on hold",
 * "graduated", ...) or of the account status ("active" finds Active students only, not Inactive ones). The database
 * keeps study status codes (ON_HOLD); the search reads their labels.
 */
public final class StudentDirectorySearch {

    private static final String[] TEXT_COLUMNS = {"user_id", "name", "email", "phone", "sponsor_code", "sponsor_name",
            "chapter_name", "institution_name", "course_name"};

    /** Filters (null / blank = no filter). sponsored: "yes" (has a sponsor) or "no". */
    public record Filters(Long chapterId, String studyStatus, String status, String sponsored, String sessionYear) {
        public Filters(Long chapterId, String studyStatus, String status, String sponsored) {
            this(chapterId, studyStatus, status, sponsored, null);
        }
    }

    private StudentDirectorySearch() {
    }

    public static List<Map<String, Object>> apply(List<Map<String, Object>> rows, String search, Filters f) {
        List<String> words = words(search);
        return rows.stream().filter(r -> passes(r, f) && words.stream().allMatch(w -> matches(r, w))).toList();
    }

    static boolean passes(Map<String, Object> r, Filters f) {
        if (f == null) return true;
        if (f.chapterId() != null && !f.chapterId().equals(number(r.get("chapter_id")))) return false;
        if (!blank(f.studyStatus()) && !f.studyStatus().equals(StudyStatus.of((String) r.get("study_status")))) return false;
        if (!blank(f.status()) && !f.status().equalsIgnoreCase(String.valueOf(r.get("status")))) return false;
        if ("yes".equalsIgnoreCase(f.sponsored()) && r.get("sponsor_id") == null) return false;
        if ("no".equalsIgnoreCase(f.sponsored()) && r.get("sponsor_id") != null) return false;
        if (!sessionYearMatches(r.get("year"), f.sessionYear())) return false;
        return true;
    }

    static boolean matches(Map<String, Object> r, String word) {
        for (String col : TEXT_COLUMNS) {
            Object v = r.get(col);
            if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(word)) return true;
        }
        return words(StudyStatus.label((String) r.get("study_status"))).contains(word)
                || words(r.get("status") == null ? null : r.get("status").toString()).contains(word);
    }

    /** sessionYear: "2025", "none" (no session year) or empty (any). */
    public static boolean sessionYearMatches(Object year, String sessionYear) {
        if (blank(sessionYear)) return true;
        if ("none".equalsIgnoreCase(sessionYear.trim())) return year == null;
        return year != null && sessionYear.trim().equals(String.valueOf(year));
    }

    /** The session years in these rows ("year"), newest first. */
    public static List<Integer> years(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> r.get("year")).filter(y -> y instanceof Number).map(y -> ((Number) y).intValue())
                .distinct().sorted(Comparator.reverseOrder()).toList();
    }

    private static List<String> words(String text) {
        if (blank(text)) return List.of();
        return Arrays.stream(text.trim().toLowerCase(Locale.ROOT).split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Long number(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}
