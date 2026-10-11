package com.rahbar.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The paged list of website records: search over the records' fields, one page, suggestions over all records. */
class WebsiteSupportListTest {

    public static class Rec {
        private final Long id;
        private final String title;
        private final String topic;

        Rec(Long id, String title, String topic) {
            this.id = id;
            this.title = title;
            this.topic = topic;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getTopic() { return topic; }
    }

    private final List<Rec> recs = List.of(new Rec(1L, "Scholarship results", "Education"), new Rec(2L, "Eid gathering", "Events"),
            new Rec(3L, "New scholarship round", "education"), new Rec(4L, "Health camp", null));

    @Test
    void withoutPagingEverythingComesBackAsBefore() {
        Map<String, Object> body = WebsiteSupport.list(recs, "anything", null, null, null, r -> Map.of());
        assertEquals(recs, body.get("data"));
        assertFalse(body.containsKey("rows"));
    }

    @Test
    void searchesFieldsPagesAndSuggestsFromAllRecords() {
        Map<String, Object> body = WebsiteSupport.list(recs, "scholarship", 0, 1, "topic", r -> Map.of(9L, "Asha"));
        assertEquals(2L, ((Number) body.get("total")).longValue());
        assertEquals(List.of(recs.get(0)), body.get("rows"));
        assertEquals(Map.of(9L, "Asha"), body.get("names"));
        assertEquals(Map.of("topic", List.of("Education", "Events")), body.get("suggestions"));
    }
}
