package com.rahbar.service;

import com.rahbar.dto.ReportFile;
import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Payment;
import com.rahbar.entity.PaymentInstallment;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sponsor downloads: receipts only for their own paid payments, Excel history, calendar reminders. */
class SponsorFilesServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentInstallmentRepository installments = mock(PaymentInstallmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final ReportService reports = mock(ReportService.class);
    private final SponsorFilesService service = new SponsorFilesService(payments, installments, users, mappings, reports);

    private static Payment payment(long id, long sponsor, String status, LocalDateTime on) {
        Payment p = new Payment();
        p.setPaymentId(id);
        p.setGrantorId(sponsor);
        p.setGranteeId(11L);
        p.setAmount(new BigDecimal("6000"));
        p.setStatus(status);
        p.setPaymentDate(on);
        return p;
    }

    private static User user(long id, String code, String name) {
        User u = new User();
        u.setId(id);
        u.setUserId(code);
        u.setName(name);
        return u;
    }

    @Test
    void aReceiptOnlyForTheSponsorsOwnPaidPayment() {
        when(payments.findById(1L)).thenReturn(Optional.of(payment(1, 5, "Paid", LocalDateTime.of(2026, 4, 2, 10, 0))));
        when(payments.findById(2L)).thenReturn(Optional.of(payment(2, 6, "Paid", LocalDateTime.of(2026, 4, 2, 10, 0)))); // someone else's
        when(payments.findById(3L)).thenReturn(Optional.of(payment(3, 5, "Pending", null)));
        when(users.findById(5L)).thenReturn(Optional.of(user(5, "SP5", "Zara")));
        when(users.findById(11L)).thenReturn(Optional.of(user(11, "ST11", "Asha")));
        when(installments.findByPaymentId(1L)).thenReturn(Optional.empty());

        byte[] pdf = service.receipt(5L, 1L);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertThrows(ApiException.class, () -> service.receipt(5L, 2L));
        assertThrows(ApiException.class, () -> service.receipt(5L, 3L));
        assertEquals("RB-PAY-000123", SponsorFilesService.receiptNo(123L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyIsOneYearOrAll() {
        when(payments.findByGrantorIdOrderByPaymentDateDesc(5L)).thenReturn(List.of(
                payment(1, 5, "Paid", LocalDateTime.of(2026, 4, 2, 10, 0)), payment(2, 5, "Paid", LocalDateTime.of(2025, 4, 2, 10, 0)),
                payment(3, 5, "Pending", LocalDateTime.of(2026, 5, 2, 10, 0))));
        when(reports.build(anyList(), anyString(), eq("excel"), anyString())).thenReturn(new ReportFile(new byte[0], "x", "y"));
        service.history(5L, 2026);
        verify(reports).build(argThat(rows -> rows.size() == 1 && "RB-PAY-000001".equals(((Map<String, Object>) rows.get(0)).get("receipt_no"))),
                eq("Rahbar_payments_2026"), eq("excel"), anyString());
        assertThrows(ApiException.class, () -> service.history(5L, 2019));
    }

    @Test
    void theCalendarHasAnAllDayEventWithAReminderPerUpcomingInstallment() {
        PaymentInstallment i = new PaymentInstallment();
        i.setInstallmentId(42L);
        i.setInstallmentNo(3);
        i.setGranteeId(11L);
        i.setAmount(new BigDecimal("6000"));
        i.setDueDate(LocalDate.of(2026, 11, 1));
        String ics = SponsorFilesService.ics(List.of(i), Map.of(11L, "Asha, Khan"), LocalDateTime.of(2026, 10, 12, 9, 0));
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("UID:rahbar-installment-42@rahbar"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261101"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261102"));
        assertTrue(ics.contains("Asha\\, Khan")); // commas escaped
        assertTrue(ics.contains("TRIGGER:-P3D"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
    }

    @Test
    void theCalendarLeavesOutPaidPastAndFormerStudentsInstallments() {
        GrantorGrantee g = new GrantorGrantee();
        g.setGrantorId(5L);
        g.setGranteeId(11L);
        when(mappings.findByGrantorId(5L)).thenReturn(List.of(g));
        PaymentInstallment upcoming = new PaymentInstallment(), paid = new PaymentInstallment(), past = new PaymentInstallment(), former = new PaymentInstallment();
        for (PaymentInstallment x : List.of(upcoming, paid, past, former)) {
            x.setGranteeId(11L);
            x.setInstallmentNo(1);
            x.setAmount(BigDecimal.TEN);
            x.setDueDate(LocalDate.now().plusDays(10));
        }
        upcoming.setInstallmentId(1L);
        paid.setInstallmentId(2L);
        paid.setPaymentId(9L);
        past.setInstallmentId(3L);
        past.setDueDate(LocalDate.now().minusDays(1));
        former.setInstallmentId(4L);
        former.setGranteeId(99L);
        when(installments.findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(5L)).thenReturn(List.of(upcoming, paid, past, former));
        String ics = new String(service.calendar(5L), StandardCharsets.UTF_8);
        assertTrue(ics.contains("rahbar-installment-1@"));
        assertFalse(ics.contains("rahbar-installment-2@") || ics.contains("rahbar-installment-3@") || ics.contains("rahbar-installment-4@"));
    }
}
