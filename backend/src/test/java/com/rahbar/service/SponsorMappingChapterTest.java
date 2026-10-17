package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.User;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.SponsorMappingHistoryRepository;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A student mapped to a sponsor takes the sponsor's chapter. */
class SponsorMappingChapterTest {

    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SponsorMappingHistoryRepository history = mock(SponsorMappingHistoryRepository.class);
    private final SponsorMappingService service = new SponsorMappingService(mappings, users, mock(NotificationService.class),
            mock(PaymentInstallmentService.class), history);

    private User user(long id, String code, Long chapterId) {
        User u = new User();
        u.setId(id);
        u.setUserId(code);
        u.setName("User " + id);
        u.setChapterId(chapterId);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void theStudentTakesTheNewSponsorsChapter() {
        User student = user(10, "ST-10", 1L);
        user(20, "SP-20", 7L);
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.empty());
        when(history.findByGranteeIdAndEndedAtIsNull(10L)).thenReturn(List.of());
        service.map(10L, 20L, "Assigned", true);
        assertEquals(7L, student.getChapterId());
        verify(users).save(student);
    }

    @Test
    void aSponsorWithoutAChapterOrTheUnassignedGrantorLeavesItAlone() {
        User student = user(10, "ST-10", 1L);
        user(21, "SP-21", null);
        User unassigned = user(12, ServiceSupport.UNASSIGNED_GRANTOR_CODE, 9L);
        when(users.findByUserId(ServiceSupport.UNASSIGNED_GRANTOR_CODE)).thenReturn(Optional.of(unassigned));
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.empty());
        when(history.findByGranteeIdAndEndedAtIsNull(10L)).thenReturn(List.of());
        service.map(10L, 21L, "Assigned", true);
        assertEquals(1L, student.getChapterId());

        GrantorGrantee gg = new GrantorGrantee();
        gg.setGranteeId(10L);
        gg.setGrantorId(21L);
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.of(gg));
        service.remap(10L, 12L); // moved to "unassigned"
        assertEquals(1L, student.getChapterId());
        verify(users, never()).save(any(User.class));
    }
}
