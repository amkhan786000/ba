package com.rahbar.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Reads user ids (users.id) from request bodies, where JSON numbers may arrive as Integer, Long or String. */
public final class Ids {

    private Ids() {}

    /** The id, or null when the value is missing or blank. */
    public static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    /** Every non-blank id in a JSON array (empty list when the value is not an array). */
    public static List<Long> toLongs(Object value) {
        List<Long> ids = new ArrayList<>();
        if (value instanceof Collection<?> items) {
            for (Object item : items) {
                Long id = toLong(item);
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }
}
