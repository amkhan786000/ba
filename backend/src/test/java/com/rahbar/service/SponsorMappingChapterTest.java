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
import static org.mockito.ArgumentMatchers.*;
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

    @Test
    void theReasonIsKeptOnTheSponsorshipThatEnds() {
        user(10, "ST-10", 1L);
        user(20, "SP-20", null);
        user(21, "SP-21", null);
        GrantorGrantee gg = new GrantorGrantee();
        gg.setGranteeId(10L);
        gg.setGrantorId(20L);
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.of(gg));
        var open = new com.rahbar.entity.SponsorMappingHistory();
        open.setGranteeId(10L);
        open.setGrantorId(20L);
        open.setStartedAt(java.time.LocalDateTime.now().minusYears(1));
        when(history.findByGranteeIdAndEndedAtIsNull(10L)).thenReturn(List.of(open));

        service.map(10L, 21L, "Assigned", true, "  Sponsor declined to continue ");
        assertNotNull(open.getEndedAt());
        assertEquals("Sponsor declined to continue", open.getEndReason());
        // and a new period with the new sponsor
        verify(history).save(argThat(h -> h.getGrantorId() == 21L && h.getEndedAt() == null));
    }

    @Test
    void onBehalfOfAnAnonymousDonorKeepsTheStudentsChapterAndTheHistory() {
        User student = user(10, "ST-10", 1L);
        user(20, "SP-20", 7L); // the sponsor's own chapter would move the student...
        GrantorGrantee[] saved = new GrantorGrantee[1];
        when(mappings.findFirstByGranteeId(10L)).thenAnswer(i -> Optional.ofNullable(saved[0]));
        when(mappings.save(any(GrantorGrantee.class))).thenAnswer(i -> { saved[0] = i.getArgument(0); return saved[0]; });
        when(history.findByGranteeIdAndEndedAtIsNull(10L)).thenReturn(List.of());

        service.map(10L, 20L, "Assigned", true, null, new com.rahbar.dto.OnBehalf(3L, " Donor A "), true);
        assertEquals(1L, student.getChapterId()); // ...but on behalf of a donor the student keeps their own
        assertTrue(saved[0].getOnBehalf());
        assertEquals(3L, saved[0].getDonorChapterId());
        assertEquals("Donor A", saved[0].getDonorLabel());
        verify(history).save(argThat(h -> h.getGrantorId() == 20L && Boolean.TRUE.equals(h.getOnBehalf()) && h.getDonorChapterId() == 3L));

        // mapping to another sponsor without saying so: their own sponsorship
        user(21, "SP-21", null);
        service.map(10L, 21L, "Assigned", false);
        assertFalse(saved[0].getOnBehalf());
        assertNull(saved[0].getDonorChapterId());
        assertEquals("on behalf of Donor A, an anonymous sponsor from Jeddah Chapter", SponsorMappingService.onBehalfText("Donor A", "Jeddah Chapter"));
        assertEquals("on behalf of an anonymous sponsor from Jeddah Chapter", SponsorMappingService.onBehalfText(null, "Jeddah Chapter"));
    }
}
