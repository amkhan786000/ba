package com.rahbar.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {

    @Test
    void sponsorsAreGroupedByHowManyStudentsTheyHave() {
        List<Map<String, Object>> perSponsor = List.of(Map.of("n", 1L), Map.of("n", 1L), Map.of("n", 3L), Map.of("n", 7L));
        List<Map<String, Object>> buckets = DashboardService.loadBuckets(perSponsor);
        assertEquals(List.of(2L, 0L, 1L, 0L, 1L), buckets.stream().map(b -> b.get("value")).toList());
        assertEquals("5 or more", buckets.get(4).get("label"));
    }

    @Test
    void percentIsRoundedAndEmptyWithoutAWhole() {
        assertEquals(67, DashboardService.percent(2, 3));
        assertNull(DashboardService.percent(0, 0));
    }

    @Test
    void pointsKeepAmountsAndTurnOtherNumbersIntoCounts() {
        assertEquals(new java.math.BigDecimal("12.50"), DashboardService.point("x", new java.math.BigDecimal("12.50")).get("value"));
        assertEquals(4L, DashboardService.point("x", 4).get("value"));
        assertEquals(0L, DashboardService.point("x", null).get("value"));
    }

    @Test
    void openStatusesAreTheUndecidedSteps() {
        assertTrue(DashboardService.OPEN.stream().allMatch(ApplicationService.VALID_STATUSES::contains));
        assertFalse(DashboardService.OPEN.contains("ACCEPTED"));
        assertFalse(DashboardService.OPEN.contains("REJECTED"));
    }
}
