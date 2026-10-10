package com.rahbar.service;

import com.rahbar.entity.PaymentInstallment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.rahbar.service.PaymentInstallmentService.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Not Due -> Due ("show as due" days before) -> Overdue (after the due date); Paid always wins. */
class InstallmentStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private static PaymentInstallment due(LocalDate date, Long paymentId) {
        PaymentInstallment i = new PaymentInstallment();
        i.setDueDate(date);
        i.setPaymentId(paymentId);
        return i;
    }

    @Test
    void statusFollowsTheNoticeWindow() {
        assertEquals(NOT_DUE, status(due(TODAY.plusDays(31), null), TODAY, 30));
        assertEquals(DUE, status(due(TODAY.plusDays(30), null), TODAY, 30));   // first day of the window
        assertEquals(DUE, status(due(TODAY.plusDays(1), null), TODAY, 30));
        assertEquals(DUE, status(due(TODAY, null), TODAY, 30));                // the due date itself
        assertEquals(OVERDUE, status(due(TODAY.minusDays(1), null), TODAY, 30));
        assertEquals(PAID, status(due(TODAY.minusDays(90), 7L), TODAY, 30));
        assertEquals(PAID, status(due(TODAY.plusDays(90), 7L), TODAY, 30));
    }

    @Test
    void zeroNoticeDaysMeansDueOnlyOnTheDay() {
        assertEquals(NOT_DUE, status(due(TODAY.plusDays(1), null), TODAY, 0));
        assertEquals(DUE, status(due(TODAY, null), TODAY, 0));
    }
}
