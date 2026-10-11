package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** REB phase 4: gate passes, admitting from an enquiry, the Hifz summary and the sponsor's receipts. */
class RebPhase4Test {

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // ---- hostel leave

    private final RebHostelLeaveRepository leaves = mock(RebHostelLeaveRepository.class);
    private final RebGuardianRepository guardians = mock(RebGuardianRepository.class);
    private final RebStudentService studentService = mock(RebStudentService.class);
    private final RebHostelService hostel = new RebHostelService(leaves, mock(RebStudentRepository.class), guardians, studentService);

    private RebStudent girl(boolean residential) {
        RebStudent g = new RebStudent();
        g.setId(1L);
        g.setName("Sana");
        g.setResidential(residential);
        when(studentService.require(1L)).thenReturn(g);
        return g;
    }

    private RebGuardian guardian(long id, long studentId, boolean mayPickUp) {
        RebGuardian x = new RebGuardian();
        x.setId(id);
        x.setStudentId(studentId);
        x.setName("Guardian " + id);
        x.setMayPickUp(mayPickUp);
        when(guardians.findById(id)).thenReturn(Optional.of(x));
        return x;
    }

    private Map<String, Object> pass(long guardianId) {
        return new HashMap<>(Map.of("studentId", 1, "guardianId", guardianId, "leaveTo", LocalDate.now().plusDays(3).toString(), "reason", "Eid"));
    }

    @Test
    void aGatePassOnlyForHostelGirlsWithAGuardianAllowedToTakeHer() {
        girl(false);
        assertThrows(ApiException.class, () -> hostel.give(pass(5)));          // day scholar
        girl(true);
        guardian(5, 1, false);
        assertThrows(ApiException.class, () -> hostel.give(pass(5)));          // not allowed to take her
        guardian(6, 2, true);
        assertThrows(ApiException.class, () -> hostel.give(pass(6)));          // another girl's guardian
        guardian(7, 1, true);
        when(leaves.save(any())).thenAnswer(i -> i.getArgument(0));
        when(leaves.findNumbersStartingWith(anyString())).thenReturn(List.of());
        RebHostelLeave l = hostel.give(pass(7));
        assertEquals("GP/" + LocalDate.now().getYear() + "/0001", l.getPassNo());
        when(leaves.findByStudentIdOrderByLeaveFromDesc(1L)).thenReturn(List.of(l));
        assertThrows(ApiException.class, () -> hostel.give(pass(7)));          // already out
    }

    @Test
    void stillOutAfterTheDayIsOverdue() {
        RebHostelLeave l = new RebHostelLeave();
        l.setStatus("OUT");
        l.setLeaveTo(LocalDate.now().minusDays(1));
        assertTrue(RebHostelService.overdue(l));
        l.setStatus("RETURNED");
        assertFalse(RebHostelService.overdue(l));
    }

    // ---- enquiries

    @Test
    void admittingFromAnEnquiryFillsTheAdmissionFromIt() {
        RebAdmissionEnquiryRepository repo = mock(RebAdmissionEnquiryRepository.class);
        RebEnquiryService service = new RebEnquiryService(repo, studentService, mock(WebsiteNotifier.class));
        RebAdmissionEnquiry e = new RebAdmissionEnquiry();
        e.setId(3L);
        e.setGirlName("Ayesha");
        e.setGuardianName("Imran");
        e.setRelation("FATHER");
        e.setPhone("9876543210");
        e.setResidential(true);
        when(repo.findById(3L)).thenReturn(Optional.of(e));
        when(studentService.admit(any())).thenReturn(40L);
        assertEquals(40L, service.admit(3L, Map.of("sectionId", 60)));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> form = ArgumentCaptor.forClass(Map.class);
        verify(studentService).admit(form.capture());
        assertEquals("Ayesha", form.getValue().get("name"));
        assertEquals(60, form.getValue().get("sectionId"));
        assertEquals("ADMITTED", e.getStatus());
        assertThrows(ApiException.class, () -> service.admit(3L, Map.of("sectionId", 60))); // only once
    }

    // ---- Quran

    private static RebQuranProgress lesson(String day, String kind, int para, boolean completed) {
        RebQuranProgress p = new RebQuranProgress();
        p.setDate(LocalDate.parse(day));
        p.setKind(kind);
        p.setPara(para);
        p.setCompletedPara(completed);
        return p;
    }

    @Test
    void hifzSummary() {
        var s = RebQuranService.summary(List.of(lesson("2026-07-03", "MANZIL", 1, false), lesson("2026-07-02", "SABAQ", 3, false),
                lesson("2026-06-20", "SABAQ", 2, true), lesson("2026-05-01", "SABAQ", 1, true)));
        assertEquals(3, s.get("current_para"));
        assertEquals(2, s.get("paras_completed"));
        assertEquals(LocalDate.parse("2026-07-03"), s.get("last_lesson"));
    }

    // ---- sponsor portal

    @Test
    void aSponsorGetsOnlyTheirOwnReceipts() {
        User me = new User();
        me.setId(9L);
        me.setRoleId(5);
        var p = new RahbarUserPrincipal(me, Set.of(), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        RebPaymentRepository payments = mock(RebPaymentRepository.class);
        RebReceiptService receipts = mock(RebReceiptService.class);
        RebSponsorPortalService portal = new RebSponsorPortalService(mock(RebSponsorshipRepository.class), mock(RebStudentRepository.class),
                mock(RebEnrollmentRepository.class), mock(RebSessionRepository.class), payments, studentService,
                mock(RebAttendanceService.class), mock(RebExamService.class), receipts);
        RebPayment family = new RebPayment();
        family.setPayer("FAMILY");
        RebPayment other = new RebPayment();
        other.setPayer("SPONSOR");
        other.setSponsorUserId(10L);
        RebPayment mine = new RebPayment();
        mine.setPayer("SPONSOR");
        mine.setSponsorUserId(9L);
        when(payments.findById(1L)).thenReturn(Optional.of(family));
        when(payments.findById(2L)).thenReturn(Optional.of(other));
        when(payments.findById(3L)).thenReturn(Optional.of(mine));
        assertThrows(ApiException.class, () -> portal.receipt(1L));
        assertThrows(ApiException.class, () -> portal.receipt(2L));
        portal.receipt(3L);
        verify(receipts).receipt(3L);
    }
}
