package com.rahbar.service;

import com.rahbar.entity.User;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The session-year dropdown on student screens: "" any year, "none" without one, else that year. */
class SessionYearFilterTest {

    @Test
    void directoryRowsMatchTheChosenYear() {
        assertTrue(StudentDirectorySearch.sessionYearMatches(2025, ""));
        assertTrue(StudentDirectorySearch.sessionYearMatches(null, null));
        assertTrue(StudentDirectorySearch.sessionYearMatches(2025, "2025"));
        assertFalse(StudentDirectorySearch.sessionYearMatches(2024, "2025"));
        assertTrue(StudentDirectorySearch.sessionYearMatches(null, "none"));
        assertFalse(StudentDirectorySearch.sessionYearMatches(2025, "none"));
        Map<String, Object> a = new HashMap<>(Map.of("year", 2024)), b = new HashMap<>(Map.of("year", 2025)), c = new HashMap<>();
        c.put("year", null);
        assertEquals(List.of(2025, 2024), StudentDirectorySearch.years(List.of(a, b, c, b)));
        assertFalse(StudentDirectorySearch.passes(b, new StudentDirectorySearch.Filters(null, null, null, null, "2024")));
        assertTrue(StudentDirectorySearch.passes(a, new StudentDirectorySearch.Filters(null, null, null, null, "2024")));
    }

    @Test
    void installmentsFollowTheStudentsYear() {
        User s = new User();
        s.setYear(2025);
        assertTrue(PaymentInstallmentService.ofSessionYear(s, null));
        assertTrue(PaymentInstallmentService.ofSessionYear(s, "2025"));
        assertFalse(PaymentInstallmentService.ofSessionYear(s, "2024"));
        assertFalse(PaymentInstallmentService.ofSessionYear(s, "none"));
        assertFalse(PaymentInstallmentService.ofSessionYear(null, "2025"));
        User noYear = new User();
        assertTrue(PaymentInstallmentService.ofSessionYear(noYear, "none"));
        assertEquals(List.of(2025), PaymentInstallmentService.sessionYears(List.of(1L, 2L), Map.of(1L, s, 2L, noYear)));
    }

    @Test
    void dashboardsAddTheYearToTheStudentQueriesSafely() {
        assertEquals("", DashboardService.sessionYearSql("s", null));
        assertEquals(" AND s.year = 2025", DashboardService.sessionYearSql("s", "2025"));
        assertEquals(" AND s.year IS NULL", DashboardService.sessionYearSql("s", "none"));
        assertThrows(com.rahbar.exception.ApiException.class, () -> DashboardService.sessionYearSql("s", "2025 OR 1=1"));
    }
}
