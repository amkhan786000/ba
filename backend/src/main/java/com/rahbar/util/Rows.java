package com.rahbar.util;

import jakarta.persistence.Column;
import jakarta.persistence.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns JPA entities into the snake_case row maps the frontend has always received
 * (the same keys a "SELECT *" used to return), e.g. {@code user_id}, {@code created_at}.
 * The users.password_hash column is never included.
 */
public final class Rows {

    private static final Set<String> HIDDEN = Set.of("password_hash");
    private static final Map<Class<?>, List<Map.Entry<String, Field>>> COLUMNS = new ConcurrentHashMap<>();

    private Rows() {}

    /** Every mapped column of the entity, keyed by column name (null when the entity is null). */
    public static Map<String, Object> of(Object entity) {
        if (entity == null) return null;
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Field> c : columns(entity.getClass())) {
            try {
                row.put(c.getKey(), c.getValue().get(entity));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return row;
    }

    /** Only the given columns, in the given order (null when the entity is null). */
    public static Map<String, Object> pick(Object entity, String... columns) {
        Map<String, Object> all = of(entity);
        return all == null ? null : ordered(all, columns);
    }

    public static List<Map<String, Object>> list(Collection<?> entities) {
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (Object e : entities) rows.add(of(e));
        return rows;
    }

    public static List<Map<String, Object>> pickAll(Collection<?> entities, String... columns) {
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (Object e : entities) rows.add(pick(e, columns));
        return rows;
    }

    /** All columns of the entity type with null values, like a LEFT JOIN that found no row. */
    public static Map<String, Object> empty(Class<?> type) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Field> c : columns(type)) row.put(c.getKey(), null);
        return row;
    }

    /** A copy of the map with exactly these keys in this order (used for report column order). */
    public static Map<String, Object> ordered(Map<String, Object> source, String... columns) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String c : columns) row.put(c, source.get(c));
        return row;
    }

    public static <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private static List<Map.Entry<String, Field>> columns(Class<?> type) {
        return COLUMNS.computeIfAbsent(type, t -> {
            // Entity's own fields first, then the inherited audit columns (matches the table column order).
            List<Class<?>> hierarchy = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) hierarchy.add(c);
            List<Map.Entry<String, Field>> result = new ArrayList<>();
            for (Class<?> c : hierarchy) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Transient.class)) continue;
                    Column col = f.getAnnotation(Column.class);
                    String name = col != null && !col.name().isEmpty() ? col.name() : snake(f.getName());
                    if (HIDDEN.contains(name)) continue;
                    f.setAccessible(true);
                    result.add(Map.entry(name, f));
                }
            }
            return result;
        });
    }

    private static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
