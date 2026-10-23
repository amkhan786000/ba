package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Students > Reference Numbers: M001-2026 for Govt colleges, PVT001-2026 for the rest, each counting on. */
class StudentReferenceServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StudentReferenceService service = new StudentReferenceService(users);
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
    void onlyTheCurrentYear() {
        ApiException e = assertThrows(ApiException.class, () -> service.generate(year - 1));
        assertTrue(e.getMessage().contains(String.valueOf(year)));
        assertThrows(ApiException.class, () -> service.generate(null));
        verify(users, never()).save(any());
    }
}
