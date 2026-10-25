package com.rahbar.service;

import com.rahbar.entity.ReferenceBatch;
import com.rahbar.entity.ReferenceCounter;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ReferenceBatchRepository;
import com.rahbar.repository.ReferenceCounterRepository;
import com.rahbar.repository.UserRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Students > Reference Numbers: M001-2026 for Govt colleges, PVT001-2026 for the rest, each counting on; all at once
 * or chapter by chapter, carrying on from the stored last number.
 */
class StudentReferenceServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ReferenceCounterRepository counters = mock(ReferenceCounterRepository.class);
    private final ReferenceBatchRepository batches = mock(ReferenceBatchRepository.class);
    private final StudentReferenceService service = new StudentReferenceService(users, counters, batches);
    /** What the counters table holds: "year/prefix" -> last number. */
    private final Map<String, ReferenceCounter> stored = new HashMap<>();

    {
        when(counters.findBySessionYearAndPrefix(anyInt(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0) + "/" + i.getArgument(1))));
        when(counters.save(any())).thenAnswer(i -> {
            ReferenceCounter c = i.getArgument(0);
            stored.put(c.getSessionYear() + "/" + c.getPrefix(), c);
            return c;
        });
    }
    private final int year = StudentReferenceService.currentYear();

    private static User student(long id, Integer year, String collegeType, String ref) {
        User u = new User();
        u.setId(id);
        u.setRoleId(6);
        u.setName("Student " + id);
        u.setStatus("active");
        u.setYear(year);
        u.setCollegeType(collegeType);
        u.setUserId(ref);
        return u;
    }

    @Test
    void govtGetsMAndEverythingElseCountsAsPrivate() {
        assertEquals("M", StudentReferenceService.prefixFor("GOVT"));
        assertEquals("PVT", StudentReferenceService.prefixFor("PRIVATE"));
        assertEquals("PVT", StudentReferenceService.prefixFor("ANY"));
        assertEquals("PVT", StudentReferenceService.prefixFor(null));
        assertEquals("M001-2026", StudentReferenceService.format("M", 1, 2026));
        assertEquals("PVT012-2026", StudentReferenceService.format("PVT", 12, 2026));
        assertEquals("M1000-2026", StudentReferenceService.format("M", 1000, 2026));
    }

    @Test
    void readsTheNumberOnlyOfTheSamePrefixAndYear() {
        assertEquals(7, StudentReferenceService.numberOf("M007-2026", "M", 2026));
        assertEquals(0, StudentReferenceService.numberOf("PVT007-2026", "M", 2026));
        assertEquals(0, StudentReferenceService.numberOf("M007-2025", "M", 2026));
        assertEquals(3, StudentReferenceService.numberOf("pvt003-2026", "PVT", 2026));
        assertEquals(0, StudentReferenceService.numberOf(null, "M", 2026));
    }

    @Test
    void numbersStudentsOfTheYearOldestFirstAndContinuesEachCounter() {
        User numbered = student(1, year, "GOVT", "M004-" + year);
        User govt = student(5, year, "GOVT", "1234"); // the code given when the application was accepted
        User priv = student(3, year, "PRIVATE", null);
        User any = student(4, year, "ANY", null);
        User unset = student(9, year, null, " ");
        User lastYear = student(2, year - 1, "GOVT", "987");
        User inactive = student(6, year, "GOVT", null);
        inactive.setStatus("Inactive");
        User merged = student(7, year, "GOVT", null);
        merged.setMergedIntoId(1L);
        List<User> all = List.of(numbered, govt, priv, any, unset, lastYear, inactive, merged);
        when(users.findByRoleId(6)).thenReturn(all);
        when(users.findAll()).thenReturn(all);
        when(users.existsByUserId("PVT002-" + year)).thenReturn(true); // another account already has it: skipped

        Map<String, Object> out = service.generate(year);

        assertEquals("M005-" + year, govt.getUserId());
        assertEquals("PVT001-" + year, priv.getUserId());
        assertEquals("PVT003-" + year, any.getUserId());
        assertEquals("PVT004-" + year, unset.getUserId());
        assertEquals("M004-" + year, numbered.getUserId());
        assertEquals("987", lastYear.getUserId());
        assertNull(inactive.getUserId());
        assertNull(merged.getUserId());
        List<?> generated = (List<?>) out.get("generated");
        assertEquals(4, generated.size());
        assertEquals("1234", ((Map<?, ?>) generated.stream().filter(r -> ("M005-" + year).equals(((Map<?, ?>) r).get("user_id"))).findFirst().orElseThrow()).get("previous_user_id"));
        assertEquals(0L, out.get("without_govt"));
        assertEquals(0L, out.get("without_private"));
    }

    @Test
    void notBeforeTheCurrentYear() {
        ApiException e = assertThrows(ApiException.class, () -> service.generate(year - 1));
        assertTrue(e.getMessage().contains(String.valueOf(year)));
        assertThrows(ApiException.class, () -> service.generate(null));
        verify(users, never()).save(any());
    }

    private User inChapter(User u, Long chapterId, String chapter) {
        u.setChapterId(chapterId);
        u.setChapterName(chapter);
        return u;
    }

    @Test
    void chapterByChapterCarriesOnFromTheStoredNumber() {
        User a1 = inChapter(student(1, year, "PRIVATE", "11"), 5L, "Patna");
        User b1 = inChapter(student(2, year, "PRIVATE", "12"), 6L, "Gaya");
        User a2 = inChapter(student(3, year, "PRIVATE", "13"), 5L, "Patna");
        User none = student(4, year, "GOVT", "14");
        List<User> all = List.of(a1, b1, a2, none);
        when(users.findByRoleId(6)).thenReturn(all);
        when(users.findAll()).thenReturn(all);

        service.generate(year, 5L); // Patna first
        assertEquals("PVT001-" + year, a1.getUserId());
        assertEquals("PVT002-" + year, a2.getUserId());
        assertEquals("12", b1.getUserId()); // Gaya not yet
        assertEquals(2, stored.get(year + "/PVT").getLastNumber());

        // a number given before goes away (e.g. the student was deleted): it is still never given again
        a2.setUserId("gone");
        service.generate(year, 6L);
        assertEquals("PVT003-" + year, b1.getUserId());

        service.generate(year, StudentReferenceService.NO_CHAPTER); // students without a chapter
        assertEquals("M001-" + year, none.getUserId());

        ArgumentCaptor<ReferenceBatch> saved = ArgumentCaptor.forClass(ReferenceBatch.class);
        verify(batches, times(3)).save(saved.capture());
        ReferenceBatch patna = saved.getAllValues().get(0);
        assertEquals("Patna", patna.getChapterName());
        assertEquals(1, patna.getFirstNumber());
        assertEquals(2, patna.getLastNumber());
        assertEquals(2, patna.getHowMany());
        assertNull(saved.getAllValues().get(2).getChapterId());
        assertFalse(saved.getAllValues().get(2).isAllChapters());
    }

    @Test
    void nothingLeftSaysSo() {
        User done = inChapter(student(1, year, "GOVT", "M001-" + year), 5L, "Patna");
        when(users.findByRoleId(6)).thenReturn(List.of(done));
        when(users.findAll()).thenReturn(List.of(done));
        ApiException e = assertThrows(ApiException.class, () -> service.generate(year));
        assertTrue(e.getMessage().contains("All reference numbers"));
        assertThrows(ApiException.class, () -> service.generate(year, 5L));
        Map<String, Object> page = service.overview(year);
        Map<?, ?> thisYear = ((List<?>) page.get("years")).stream().map(r -> (Map<?, ?>) r).filter(r -> r.get("year").equals(year)).findFirst().orElseThrow();
        assertEquals(0L, thisYear.get("without"));
        assertEquals("M002-" + year, page.get("next_govt"));
    }
}
