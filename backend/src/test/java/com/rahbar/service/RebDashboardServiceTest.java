package com.rahbar.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class RebDashboardServiceTest {

    @Test
    void monthsCoverTheSessionAndAreCapped() {
        var m = RebDashboardService.months(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 10, 13));
        assertEquals(7, m.size());
        assertEquals(YearMonth.of(2026, 4), m.get(0));
        assertEquals(YearMonth.of(2026, 10), m.get(6));
        assertEquals(24, RebDashboardService.months(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)).size());
    }

    @Test
    void percentNeedsSomethingToCount() {
        assertEquals(75, RebDashboardService.percent(3, 4));
        assertNull(RebDashboardService.percent(0, 0));
    }
}
