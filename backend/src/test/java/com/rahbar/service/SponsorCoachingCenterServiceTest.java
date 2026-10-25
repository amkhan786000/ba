package com.rahbar.service;

import com.rahbar.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Edit Sponsor > coaching centers: the ticked centers replace the old ones; an unknown center is refused. */
class SponsorCoachingCenterServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SponsorCoachingCenterService service = new SponsorCoachingCenterService(jdbc);

    private void centersExist(long... ids) {
        when(jdbc.queryForObject(contains("FROM rcc_centers"), eq(Long.class), any(Object[].class))).thenAnswer(i -> {
            long id = (Long) i.getArgument(2);
            for (long x : ids) if (x == id) return 1L;
            return 0L;
        });
    }

    @Test
    void theTickedCentersReplaceTheOldOnes() {
        centersExist(3, 4);
        service.set(5L, List.of(3L, 4L, 3L));
        verify(jdbc).update("DELETE FROM sponsor_coaching_centers WHERE sponsor_user_id = ?", 5L);
        verify(jdbc, times(1)).update(startsWith("INSERT INTO sponsor_coaching_centers"), eq(5L), eq(3L)); // ticked twice, saved once
        verify(jdbc, times(1)).update(startsWith("INSERT INTO sponsor_coaching_centers"), eq(5L), eq(4L));
    }

    @Test
    void noneClearsThem() {
        service.set(5L, List.of());
        verify(jdbc).update("DELETE FROM sponsor_coaching_centers WHERE sponsor_user_id = ?", 5L);
        verify(jdbc, never()).update(startsWith("INSERT"), any(Object[].class));
    }

    @Test
    void anUnknownCenterIsRefusedAndNothingChanges() {
        centersExist(3);
        assertThrows(ApiException.class, () -> service.set(5L, List.of(3L, 99L)));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
