package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A student who graduates (early or on time) keeps her plan: what she paid stays Paid, the rest is "Not needed"
 * (never due, overdue or reminded). A payment found later pays one of them after all.
 */
class GraduatedInstallmentsTest {

    private final PaymentInstallmentRepository installments = mock(PaymentInstallmentRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentInstitutionCourseRepository courses = mock(StudentInstitutionCourseRepository.class);
    private final PaymentScheduleRepository configs = mock(PaymentScheduleRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentInstallmentService service = new PaymentInstallmentService(installments, mappings, users, courses,
            configs, payments, mock(PlatformTransactionManager.class), mock(com.rahbar.security.StudentScope.class));
    private final User student = new User();

    @BeforeEach
    void graduateWithAFourInstallmentPlan() {
        student.setId(7L);
        student.setRoleId(6);
        student.setStatus("Active");
        student.setYear(2025);
        student.setPaymentStartDate(LocalDate.of(2025, 1, 1));
        student.setStudyStatus(StudyStatus.GRADUATED);
        when(users.findById(7L)).thenReturn(Optional.of(student));
        GrantorGrantee gg = new GrantorGrantee();
        gg.setGranteeId(7L);
        gg.setGrantorId(20L);
        when(mappings.findFirstByGranteeId(7L)).thenReturn(Optional.of(gg));
        when(courses.findCourseInfo(7L)).thenReturn(List.of(Map.of("number_of_semesters", 2)));
        PaymentSchedule config = new PaymentSchedule();
        config.setAmount(new BigDecimal("1000"));
        config.setFrequencyMonths(3); // 2 semesters = 12 months -> 4 installments
        when(configs.findByYearAndStatus(2025, 1)).thenReturn(List.of(config));
    }

    private Payment paid(long id) {
        Payment p = new Payment();
        p.setPaymentId(id);
        p.setStatus("Paid");
        p.setPaymentDate(LocalDate.of(2025, 1, 5).atStartOfDay());
        return p;
    }

    private PaymentInstallment row(int no, Long paymentId) {
        PaymentInstallment r = new PaymentInstallment();
        r.setGranteeId(7L);
        r.setGrantorId(20L);
        r.setInstallmentNo(no);
        r.setDueDate(LocalDate.of(2025, 1, 1).plusMonths(3L * (no - 1)));
        r.setPaymentId(paymentId);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<PaymentInstallment> saved() {
        ArgumentCaptor<List<PaymentInstallment>> c = ArgumentCaptor.forClass(List.class);
        verify(installments).saveAll(c.capture());
        return c.getValue();
    }

    @Test
    void whatWasntPaidIsNotNeeded() {
        // paid the first; the rest had been removed when she graduated (before this change)
        when(installments.findByGranteeIdOrderByInstallmentNoAsc(7L)).thenReturn(List.of(row(1, 100L)));
        when(payments.findByGranteeIdOrderByPaymentDateAsc(7L)).thenReturn(List.of(paid(100L)));
        service.sync(7L);
        List<PaymentInstallment> rows = saved();
        assertEquals(4, rows.size());
        assertEquals(PaymentInstallmentService.PAID, PaymentInstallmentService.status(rows.get(0), LocalDate.now(), 30));
        assertFalse(rows.get(0).isNotNeeded());
        for (PaymentInstallment r : rows.subList(1, 4)) {
            assertTrue(r.isNotNeeded());
            assertFalse(r.isOpen());
            assertEquals(PaymentInstallmentService.NOT_NEEDED, PaymentInstallmentService.status(r, LocalDate.now(), 30));
        }
        verify(installments, never()).deleteAll(argThat(l -> l.iterator().hasNext()));
    }

    @Test
    void aPaymentFoundLaterStillPaysOne() {
        PaymentInstallment two = row(2, null);
        two.setNotNeeded(true);
        when(installments.findByGranteeIdOrderByInstallmentNoAsc(7L)).thenReturn(List.of(row(1, 100L), two));
        when(payments.findByGranteeIdOrderByPaymentDateAsc(7L)).thenReturn(List.of(paid(100L), paid(101L)));
        service.sync(7L);
        PaymentInstallment after = saved().stream().filter(r -> r.getInstallmentNo() == 2).findFirst().orElseThrow();
        assertEquals(101L, after.getPaymentId());
        assertFalse(after.isNotNeeded());
    }

    @Test
    void graduatedWithoutASponsorKeepsHerRowsAsNotNeeded() {
        when(mappings.findFirstByGranteeId(7L)).thenReturn(Optional.empty());
        when(installments.findByGranteeIdOrderByInstallmentNoAsc(7L)).thenReturn(List.of(row(1, 100L), row(2, null)));
        when(payments.findByGranteeIdOrderByPaymentDateAsc(7L)).thenReturn(List.of(paid(100L)));
        service.sync(7L);
        List<PaymentInstallment> rows = saved();
        assertEquals(2, rows.size());
        assertTrue(rows.get(1).isNotNeeded());
    }
}
