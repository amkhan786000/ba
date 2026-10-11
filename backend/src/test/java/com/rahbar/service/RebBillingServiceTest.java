package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** REB bills: what goes on a girl's bill, concessions, the sponsor share, payments and cancelling them. */
class RebBillingServiceTest {

    private final RebInvoiceRepository invoices = mock(RebInvoiceRepository.class);
    private final RebInvoiceLineRepository lines = mock(RebInvoiceLineRepository.class);
    private final RebPaymentRepository payments = mock(RebPaymentRepository.class);
    private final RebPaymentAllocationRepository allocations = mock(RebPaymentAllocationRepository.class);
    private final RebFeeStructureRepository structures = mock(RebFeeStructureRepository.class);
    private final RebFeeHeadRepository heads = mock(RebFeeHeadRepository.class);
    private final RebConcessionRepository concessions = mock(RebConcessionRepository.class);
    private final RebStudentRepository students = mock(RebStudentRepository.class);
    private final RebEnrollmentRepository enrollments = mock(RebEnrollmentRepository.class);
    private final RebSectionRepository sections = mock(RebSectionRepository.class);
    private final RebSessionRepository sessions = mock(RebSessionRepository.class);
    private final RebSetupService setup = mock(RebSetupService.class);
    private final RebStudentService studentService = mock(RebStudentService.class);
    private final RebSponsorshipService sponsorships = mock(RebSponsorshipService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RebBillingService service = new RebBillingService(invoices, lines, payments, allocations, structures, heads, concessions,
            students, enrollments, sections, sessions, setup, studentService, sponsorships, users);

    private final RebStudent day = girl(1, false), hostel = girl(2, true);

    private static RebStudent girl(long id, boolean residential) {
        RebStudent s = new RebStudent();
        s.setId(id);
        s.setName("Girl " + id);
        s.setAdmissionNo("REB/2026/000" + id);
        s.setResidential(residential);
        return s;
    }

    private static RebFeeHead head(long id, String name, String frequency, boolean residentialOnly) {
        RebFeeHead h = new RebFeeHead();
        h.setId(id);
        h.setName(name);
        h.setFrequency(frequency);
        h.setResidentialOnly(residentialOnly);
        return h;
    }

    private static RebFeeStructure fee(long headId, String amount, Integer month) {
        RebFeeStructure f = new RebFeeStructure();
        f.setSessionId(1L);
        f.setClassId(6L);
        f.setFeeHeadId(headId);
        f.setAmount(new BigDecimal(amount));
        f.setBillMonth(month);
        return f;
    }

    private static RebEnrollment seat(long studentId) {
        RebEnrollment e = new RebEnrollment();
        e.setStudentId(studentId);
        e.setSessionId(1L);
        e.setSectionId(60L);
        return e;
    }

    @BeforeEach
    void setUp() {
        RebSession session = new RebSession();
        session.setId(1L);
        session.setName("2026");
        session.setStartDate(LocalDate.of(2026, 1, 1));
        session.setEndDate(LocalDate.of(2026, 12, 31));
        when(sessions.findAll()).thenReturn(List.of(session));
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        RebSection six = new RebSection();
        six.setId(60L);
        six.setClassId(6L);
        when(sections.findBySessionId(1L)).thenReturn(List.of(six));
        when(studentService.sectionLabels()).thenReturn(Map.of(60L, "Class 6 A"));
        when(heads.findAll()).thenReturn(List.of(head(1, "Tuition", "MONTHLY", false), head(2, "Hostel & food", "MONTHLY", true),
                head(3, "Exam", "ANNUAL", false), head(4, "Admission", "ONE_TIME", false)));
        when(structures.findBySessionId(1L)).thenReturn(List.of(fee(1, "300", null), fee(2, "700", null), fee(3, "200", 3), fee(4, "1000", null)));
        when(enrollments.findBySessionId(1L)).thenReturn(List.of(seat(1), seat(2)));
        when(students.findById(1L)).thenReturn(Optional.of(day));
        when(students.findById(2L)).thenReturn(Optional.of(hostel));
        when(setup.setting(eq("due_day"), any())).thenReturn("10");
        when(sponsorships.covering(anyLong(), anyString())).thenReturn(Optional.empty());
    }

    private RebBillingService.Planned planned(List<RebBillingService.Planned> plan, RebStudent s) {
        return plan.stream().filter(p -> p.student() == s).findFirst().orElseThrow();
    }

    @Test
    void theBillHasMonthlyHeadsHostelOnlyForResidentsAnnualInItsMonthAndOneTimeOnce() {
        var july = service.plan("2026-07");
        assertEquals(new BigDecimal("1300.00"), planned(july, day).invoice().getNet());     // tuition + admission (first bill)
        assertEquals(new BigDecimal("2000.00"), planned(july, hostel).invoice().getNet());  // + hostel & food
        assertEquals(LocalDate.of(2026, 7, 10), planned(july, day).invoice().getDueDate());

        var march = service.plan("2026-03");
        assertEquals(new BigDecimal("1500.00"), planned(march, day).invoice().getNet());    // + exam in March

        // admission already billed once: not again
        RebInvoice first = new RebInvoice();
        first.setId(9L);
        first.setStudentId(1L);
        first.setStatus("PAID");
        when(invoices.findAll()).thenReturn(List.of(first));
        RebInvoiceLine admission = new RebInvoiceLine();
        admission.setInvoiceId(9L);
        admission.setFeeHeadId(4L);
        when(lines.findAll()).thenReturn(List.of(admission));
        assertEquals(new BigDecimal("300.00"), planned(service.plan("2026-08"), day).invoice().getNet());
    }

    @Test
    void concessionsAndTheSponsorShare() {
        RebConcession half = new RebConcession();
        half.setStudentId(2L);
        half.setSessionId(1L);
        half.setFeeHeadId(2L);
        half.setKind("PERCENT");
        half.setValue(new BigDecimal("50"));
        RebConcession flat = new RebConcession();
        flat.setStudentId(2L);
        flat.setSessionId(1L);
        flat.setKind("AMOUNT");
        flat.setValue(new BigDecimal("100"));
        flat.setFromPeriod("2026-08"); // not yet in July
        when(concessions.findBySessionId(1L)).thenReturn(List.of(half, flat));
        RebSponsorship sp = new RebSponsorship();
        sp.setId(5L);
        sp.setMonthlyAmount(new BigDecimal("500"));
        when(sponsorships.covering(2L, "2026-07")).thenReturn(Optional.of(sp));

        RebInvoice bill = planned(service.plan("2026-07"), hostel).invoice();
        assertEquals(new BigDecimal("2000.00"), bill.getGross());
        assertEquals(new BigDecimal("350.00"), bill.getConcession());   // half of hostel & food
        assertEquals(new BigDecimal("1650.00"), bill.getNet());
        assertEquals(new BigDecimal("500.00"), bill.getSponsorShare());
        assertEquals(new BigDecimal("1150.00"), bill.getFamilyShare());
        assertEquals(5L, bill.getSponsorshipId());
    }

    @Test
    void freeGirlsAndGirlsAlreadyBilledAreSkipped() {
        day.setFeePlan("FREE");
        RebInvoice existing = new RebInvoice();
        existing.setStudentId(2L);
        existing.setStatus("DUE");
        when(invoices.findByPeriod("2026-07")).thenReturn(List.of(existing));
        var plan = service.plan("2026-07");
        assertEquals("Free fee plan", planned(plan, day).skipped());
        assertTrue(planned(plan, hostel).skipped().startsWith("Already has a bill"));

        existing.setStatus("CANCELLED"); // a cancelled bill can be made again
        assertNull(planned(service.plan("2026-07"), hostel).skipped());
    }

    private RebInvoice openBill(long id, String period, String family) {
        RebInvoice i = new RebInvoice();
        i.setId(id);
        i.setStudentId(1L);
        i.setSessionId(1L);
        i.setPeriod(period);
        i.setInvoiceNo("B-" + id);
        i.setDueDate(YearMonth.parse(period).atDay(10));
        i.setNet(new BigDecimal(family));
        i.setFamilyShare(new BigDecimal(family));
        return i;
    }

    @Test
    void aPaymentFillsTheOldestBillsAndCantBeMoreThanIsDue() {
        when(studentService.require(1L)).thenReturn(day);
        RebInvoice june = openBill(1, "2026-06", "300"), july = openBill(2, "2026-07", "300");
        when(invoices.findByStudentIdOrderByPeriodAsc(1L)).thenReturn(List.of(june, july));
        when(setup.setting(eq("receipt_prefix"), any())).thenReturn("REB-R");
        when(payments.findNumbersStartingWith("REB-R/2026/")).thenReturn(List.of("REB-R/2026/0004"));

        assertThrows(ApiException.class, () -> service.collect(new HashMap<>(Map.of("studentId", 1, "amount", "700"))));

        RebPayment p = service.collect(new HashMap<>(Map.of("studentId", 1, "amount", "450", "method", "CASH")));
        assertEquals("REB-R/2026/0005", p.getReceiptNo());
        assertEquals("PAID", june.getStatus());
        assertEquals("PARTIAL", july.getStatus());
        assertEquals(new BigDecimal("150.00"), july.getFamilyPaid());
        ArgumentCaptor<RebPaymentAllocation> a = ArgumentCaptor.forClass(RebPaymentAllocation.class);
        verify(allocations, times(2)).save(a.capture());
        assertEquals(new BigDecimal("300"), a.getAllValues().get(0).getAmount().stripTrailingZeros().setScale(0));
    }

    @Test
    void cancellingAReceiptMakesItsBillsUnpaidAgain() {
        RebInvoice june = openBill(1, "2026-06", "300");
        june.setFamilyPaid(new BigDecimal("300"));
        june.setStatus("PAID");
        RebPayment p = new RebPayment();
        p.setId(7L);
        p.setStatus("VALID");
        RebPaymentAllocation a = new RebPaymentAllocation();
        a.setInvoiceId(1L);
        a.setShare("FAMILY");
        a.setAmount(new BigDecimal("300"));
        when(payments.findById(7L)).thenReturn(Optional.of(p));
        when(allocations.findByPaymentId(7L)).thenReturn(List.of(a));
        when(invoices.findById(1L)).thenReturn(Optional.of(june));
        service.cancelPayment(7L, Map.of("reason", "Wrong girl"));
        assertEquals("CANCELLED", p.getStatus());
        assertEquals("DUE", june.getStatus());
        assertEquals(0, june.getFamilyPaid().signum());
        assertThrows(ApiException.class, () -> service.cancelPayment(7L, Map.of("reason", "again")));
    }

    @Test
    void amountsInWords() {
        assertEquals("Twelve Thousand Five Hundred and Fifty", RebReceiptService.words(new BigDecimal("12550")));
        assertEquals("One Lakh Twenty Thousand", RebReceiptService.words(new BigDecimal("120000")));
        assertEquals("Five Hundred and Fifty Paise", RebReceiptService.words(new BigDecimal("500.50")).replace("Hundred and", "Hundred and"));
    }
}
