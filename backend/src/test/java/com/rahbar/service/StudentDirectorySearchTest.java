package com.rahbar.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Student Directory search: every column, word by word, study status and account status by their labels. */
class StudentDirectorySearchTest {

    private static Map<String, Object> row(String ref, String name, String phone, String chapter, String course,
                                           String study, String status, Long sponsorId) {
        Map<String, Object> r = new HashMap<>();
        r.put("user_id", ref);
        r.put("name", name);
        r.put("email", name.toLowerCase().replace(' ', '.') + "@example.com");
        r.put("phone", phone);
        r.put("chapter_id", "Patna".equals(chapter) ? 1L : 2L);
        r.put("chapter_name", chapter);
        r.put("course_name", course);
        r.put("institution_name", "NIT Patna".equals(course) ? null : "Some College");
        r.put("study_status", study);
        r.put("status", status);
        r.put("sponsor_id", sponsorId);
        r.put("sponsor_name", sponsorId == null ? null : "Bilal Ahmed");
        r.put("sponsor_code", sponsorId == null ? null : "SP" + sponsorId);
        return r;
    }

    private final List<Map<String, Object>> rows = List.of(
            row("ST001", "Asha Khan", "9876500001", "Patna", "B.Tech", null, "Active", 7L),
            row("ST002", "Sana Parveen", "9876500002", "Gaya", "MBBS", "ON_HOLD", "Active", null),
            row("ST003", "Imran Ali", "9876500003", "Patna", "B.Com", "GRADUATED", "Inactive", 7L),
            row("ST004", "Zoya Noor", "9876500004", "Gaya", "B.Tech", "DROPPED_OUT", "Active", null));

    private List<String> refs(String search, StudentDirectorySearch.Filters f) {
        return StudentDirectorySearch.apply(rows, search, f).stream().map(r -> (String) r.get("user_id")).toList();
    }

    @Test
    void searchesEveryTextColumnWordByWord() {
        assertEquals(List.of("ST002"), refs("9876500002", null));
        assertEquals(List.of("ST001", "ST003"), refs("patna", null));
        assertEquals(List.of("ST001", "ST004"), refs("b.tech", null));
        assertEquals(List.of("ST001"), refs("asha patna", null));
        assertEquals(List.of("ST001", "ST003"), refs("bilal", null));
        assertEquals(List.of("ST001", "ST003"), refs("sp7", null));
        assertEquals(rows.size(), refs("  ", null).size());
    }

    @Test
    void studyStatusAndAccountStatusMatchTheirLabelsAsWholeWords() {
        assertEquals(List.of("ST002"), refs("on hold", null));
        assertEquals(List.of("ST003"), refs("graduated patna", null));
        assertEquals(List.of("ST004"), refs("dropped", null));
        assertEquals(List.of("ST001"), refs("studying", null)); // no status counts as Studying
        assertEquals(List.of("ST001", "ST002", "ST004"), refs("active", null));
        assertEquals(List.of("ST003"), refs("inactive", null));
        assertEquals(List.of(), refs("hol", null)); // part of a label is not enough
    }

    @Test
    void filtersCombineWithTheSearch() {
        assertEquals(List.of("ST002", "ST004"), refs(null, new StudentDirectorySearch.Filters(2L, null, null, null)));
        assertEquals(List.of("ST001"), refs(null, new StudentDirectorySearch.Filters(null, "STUDYING", null, null)));
        assertEquals(List.of("ST003"), refs(null, new StudentDirectorySearch.Filters(null, null, "Inactive", null)));
        assertEquals(List.of("ST002", "ST004"), refs(null, new StudentDirectorySearch.Filters(null, null, null, "no")));
        assertEquals(List.of("ST004"), refs("b.tech", new StudentDirectorySearch.Filters(null, null, null, "no")));
    }
}
