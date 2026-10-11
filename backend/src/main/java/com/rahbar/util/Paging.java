package com.rahbar.util;

import org.springframework.data.domain.Page;

import java.util.*;
import java.util.function.Function;

/**
 * Server-side paging for admin tables: the screen sends page (0-based) and size, and gets back
 * { total, page, size, rows } with only that page's rows. Lists worked out in Java (scope, derived status) are
 * filtered first and only the rows of the requested page are built.
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 200;

    private Paging() {
    }

    public static int size(Integer size) {
        return size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public static int page(Integer page) {
        return page == null ? 0 : Math.max(page, 0);
    }

    /** One page of an already filtered list; build turns just that page's items into rows. */
    public static <T> Map<String, Object> of(List<T> filtered, Integer page, Integer size, Function<List<T>, List<Map<String, Object>>> build) {
        int s = size(size);
        int p = page(page);
        int from = (int) Math.min((long) p * s, filtered.size());
        List<T> slice = filtered.subList(from, Math.min(from + s, filtered.size()));
        return body(filtered.size(), p, s, build.apply(slice));
    }

    /** One page of rows that are already built. */
    public static Map<String, Object> of(List<Map<String, Object>> filtered, Integer page, Integer size) {
        return of(filtered, page, size, Function.identity());
    }

    /** A page read from the database. */
    public static <T> Map<String, Object> of(Page<T> result, Function<List<T>, List<Map<String, Object>>> build) {
        return body(result.getTotalElements(), result.getNumber(), result.getSize(), build.apply(result.getContent()));
    }

    private static Map<String, Object> body(long total, int page, int size, List<Map<String, Object>> rows) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("page", page);
        body.put("size", size);
        body.put("rows", rows);
        return body;
    }

    /** True when every word of q appears in one of the values (case-insensitive); a blank q matches everything. */
    public static boolean matches(String q, Object... values) {
        if (q == null || q.isBlank()) return true;
        StringBuilder all = new StringBuilder();
        for (Object v : values) if (v != null) all.append(v.toString().toLowerCase(Locale.ROOT)).append(' ');
        String text = all.toString();
        for (String w : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) if (!text.contains(w)) return false;
        return true;
    }
}
