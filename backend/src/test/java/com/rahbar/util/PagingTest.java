package com.rahbar.util;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PagingTest {

    @Test
    void returnsOnlyTheRequestedPageAndBuildsOnlyIt() {
        List<Integer> all = IntStream.rangeClosed(1, 53).boxed().toList();
        List<Integer> built = new ArrayList<>();
        Map<String, Object> body = Paging.of(all, 2, 25, slice -> {
            built.addAll(slice);
            return slice.stream().map(i -> Map.<String, Object>of("n", i)).toList();
        });
        assertEquals(53L, ((Number) body.get("total")).longValue());
        assertEquals(List.of(51, 52, 53), built);
        assertEquals(3, ((List<?>) body.get("rows")).size());
    }

    @Test
    void pastTheEndIsEmptyAndSizeIsCapped() {
        Map<String, Object> body = Paging.of(List.of(1, 2, 3), 9, 10_000, s -> s.stream().map(i -> Map.<String, Object>of("n", i)).toList());
        assertEquals(List.of(), body.get("rows"));
        assertEquals(Paging.MAX_SIZE, body.get("size"));
        assertEquals(0, Paging.of(List.of(1), -4, null, s -> List.of()).get("page"));
    }

    @Test
    void matchesEveryWordInAnyValue() {
        assertTrue(Paging.matches(null, "x"));
        assertTrue(Paging.matches("  ", "x"));
        assertTrue(Paging.matches("asha ST12", "Asha Khan", "st123", null));
        assertFalse(Paging.matches("asha patna", "Asha Khan", "ST123"));
    }
}
