package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sponsor Follow-up: who counts as stopped paying / without a student, and who gets the follow-up email. */
class SponsorFollowupServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PaymentInstallmentRepository installments = mock(PaymentInstallmentRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final SponsorMappingHistoryRepository history = mock(SponsorMappingHistoryRepository.class);
    private final SponsorFollowupRepository followups = mock(SponsorFollowupRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final SponsorFollowupService service = new SponsorFollowupService(users, installments, mappings, history, followups, email);

    private final User asha = sponsor(1L, "Asha", "asha@example.com", "Active");
    private final User bilal = sponsor(2L, "Bilal", "bilal@example.com", "Active");
    private final User noMail = sponsor(3L, "Chand", "chand@rahbar.com", "Active");
    private final User gone = sponsor(4L, "Dawood", "dawood@example.com", "Inactive");

    private static User sponsor(Long id, String name, String mail, String status) {
        User u = new User();
        u.setId(id);
        u.setUserId("SP" + id);
        u.setName(name);
        u.setEmail(mail);
        u.setStatus(status);
        u.setRoleId(3);
        return u;
    }

    private static PaymentInstallment due(Long grantor, Long grantee, LocalDate on, String amount, Long paymentId) {
        PaymentInstallment i = new PaymentInstallment();
        i.setGrantorId(grantor);
        i.setGranteeId(grantee);
        i.setDueDate(on);
        i.setAmount(new BigDecimal(amount));
        i.setPaymentId(paymentId);
        return i;
    }

    private static GrantorGrantee mapping(Long grantor) {
        GrantorGrantee m = new GrantorGrantee();
        m.setGrantorId(grantor);
        m.setGranteeId(100L);
        return m;
    }

    private static SponsorFollowup followup(Long sponsor, LocalDateTime at) {
        SponsorFollowup f = new SponsorFollowup();
        f.setSponsorUserId(sponsor);
        f.setKind("LAPSED");
        f.setChannel("CALL");
        f.setCreatedAt(at);
        return f;
    }

    @BeforeEach
    void setUp() {
        when(users.findByRoleIdIn(anyList())).thenReturn(List.of(asha, bilal, noMail, gone));
        when(followups.findAllByOrderByIdDesc()).thenReturn(List.of());
        when(followups.findBySponsorUserIdOrderByIdDesc(anyLong())).thenReturn(List.of());
        when(followups.save(any())).thenAnswer(i -> i.getArgument(0));
        when(email.send(any(), anyString(), anyMap())).thenReturn(true);
    }

    @Test
    void lapsedCountsOnlyUnpaidInstallmentsPastTheCutoffOfActiveSponsors() {
        LocalDate today = LocalDate.now();
        when(installments.findAllByOrderByGranteeIdAscInstallmentNoAsc()).thenReturn(List.of(
                due(1L, 10L, today.minusDays(90), "1000", null),
                due(1L, 11L, today.minusDays(45), "500", null),
                due(1L, 10L, today.minusDays(60), "1000", 77L),  // paid
                due(2L, 12L, today.minusDays(10), "800", null),  // not late enough
                due(4L, 13L, today.minusDays(90), "800", null))); // inactive sponsor

        List<Map<String, Object>> rows = service.lapsed(30);
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);
        assertEquals(1L, r.get("sponsor_id"));
        assertEquals(2, r.get("students"));
        assertEquals(2, r.get("overdue_count"));
        assertNull(r.get("overdue_amount")); // amounts are never shown
        assertEquals(today.minusDays(90), r.get("oldest_due"));

        assertEquals(List.of(1L, 2L), service.lapsed(0).stream().map(x -> x.get("sponsor_id")).toList());
    }

    @Test
    void availableListsActiveSponsorsWithoutAStudentWithTheirLastOne() {
        when(mappings.findAll()).thenReturn(List.of(mapping(1L)));
        SponsorMappingHistory h = new SponsorMappingHistory();
        h.setGrantorId(2L);
        h.setGranteeId(50L);
        h.setStartedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        h.setEndedAt(LocalDateTime.of(2026, 3, 31, 0, 0));
        when(history.findByGrantorIdOrderByStartedAtDesc(2L)).thenReturn(List.of(h));
        User student = new User();
        student.setName("Farah");
        when(users.findById(50L)).thenReturn(Optional.of(student));

        List<Map<String, Object>> rows = service.available();
        assertEquals(List.of(2L, 3L), rows.stream().map(r -> r.get("sponsor_id")).toList());
        assertEquals("Farah", rows.get(0).get("last_student"));
        assertEquals(LocalDate.of(2026, 3, 31), rows.get(0).get("since"));
        assertEquals(true, rows.get(1).get("never_sponsored"));
        assertEquals(false, rows.get(1).get("has_email"));
        assertFalse(rows.get(0).containsKey("email"));
    }

    @Test
    void emailSkipsNoEmailRecentlyContactedAndThoseWithAStudent() {
        when(mappings.findAll()).thenReturn(List.of(mapping(1L)));
        when(followups.findBySponsorUserIdOrderByIdDesc(2L)).thenReturn(List.of(followup(2L, LocalDateTime.now().minusDays(2))));

        Map<String, Object> res = service.email(Map.of("kind", "AVAILABLE", "sponsorIds", List.of(1, 2, 3, 4)));
        assertEquals(0, res.get("sent"));
        @SuppressWarnings("unchecked") List<String> skipped = (List<String>) res.get("skipped");
        assertEquals(List.of("Asha: has a student now", "Bilal: contacted in the last 7 days", "Chand: no email address"), skipped);
        verify(email, never()).send(any(), anyString(), anyMap());

        res = service.email(Map.of("kind", "AVAILABLE", "sponsorIds", List.of(2), "force", true, "message", "We miss you"));
        assertEquals(1, res.get("sent"));
        verify(email).send(eq(EmailType.SPONSOR_INVITE_ANOTHER), eq("bilal@example.com"), argThat(v -> "We miss you".equals(v.get("message"))));
        verify(followups).save(argThat(f -> "EMAIL".equals(f.getChannel()) && f.getSponsorUserId() == 2L));
    }

    @Test
    void lapsedEmailCarriesWhatIsOverdueAndAFailedSendIsNotLogged() {
        when(installments.findAllByOrderByGranteeIdAscInstallmentNoAsc()).thenReturn(List.of(
                due(1L, 10L, LocalDate.of(2026, 1, 5), "1200.00", null)));
        Map<String, Object> res = service.email(Map.of("kind", "LAPSED", "sponsorIds", List.of(1, 2)));
        assertEquals(1, res.get("sent"));
        verify(email).send(eq(EmailType.SPONSOR_FOLLOWUP_OVERDUE), eq("asha@example.com"),
                argThat(v -> Integer.valueOf(1).equals(v.get("overdue_count")) && !v.containsKey("overdue_amount")
                        && "5 January 2026".equals(v.get("oldest_due"))));
        assertTrue(((List<?>) res.get("skipped")).contains("Bilal: nothing overdue"));

        reset(followups);
        when(followups.findBySponsorUserIdOrderByIdDesc(anyLong())).thenReturn(List.of());
        when(followups.findAllByOrderByIdDesc()).thenReturn(List.of());
        when(email.send(any(), anyString(), anyMap())).thenReturn(false);
        res = service.email(Map.of("kind", "LAPSED", "sponsorIds", List.of(1)));
        assertEquals(0, res.get("sent"));
        verify(followups, never()).save(any());
    }

    @Test
    void notesNeedAnActiveSponsorAndText() {
        SponsorFollowup f = service.record(Map.of("sponsorId", 1, "kind", "AVAILABLE", "channel", "CALL", "note", "Will take a student in June"));
        assertEquals("CALL", f.getChannel());
        assertEquals("AVAILABLE", f.getKind());
        assertThrows(ApiException.class, () -> service.record(Map.of("sponsorId", 4, "note", "Inactive sponsor")));
        assertThrows(ApiException.class, () -> service.record(Map.of("sponsorId", 1)));
        assertThrows(ApiException.class, () -> service.email(Map.of("kind", "LAPSED", "sponsorIds", List.of())));
    }
}
