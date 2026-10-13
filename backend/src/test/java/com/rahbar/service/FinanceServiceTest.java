package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The two books of income & expenses: kept apart, their yearly summaries, and what the website may show. */
class FinanceServiceTest {

    private final FinCategoryRepository categories = mock(FinCategoryRepository.class);
    private final FinEntryRepository entries = mock(FinEntryRepository.class);
    private final FinPublishedYearRepository published = mock(FinPublishedYearRepository.class);
    private final WebProjectRepository projects = mock(WebProjectRepository.class);
    private final PaymentRepository sponsorPayments = mock(PaymentRepository.class);
    private final RebPaymentRepository rebPayments = mock(RebPaymentRepository.class);
    private final FinanceService service = new FinanceService(categories, entries, published, projects, sponsorPayments, rebPayments, mock(UserRepository.class));

    private static FinCategory cat(long id, String name, String kind) {
        FinCategory c = new FinCategory();
        c.setId(id);
        c.setName(name);
        c.setKind(kind);
        return c;
    }

    private static FinEntry entry(String kind, long cat, Long project, String amount) {
        FinEntry e = new FinEntry();
        e.setKind(kind);
        e.setCategoryId(cat);
        e.setProjectId(project);
        e.setAmount(new BigDecimal(amount));
        e.setEntryDate(LocalDate.of(2026, 3, 1));
        e.setParty("Somebody private");
        return e;
    }

    @Test
    void rahbarsSummaryAddsScholarshipsButNeverRebFees() {
        when(categories.findByBookOrderByKindAscNameAsc("RAHBAR")).thenReturn(List.of(cat(1, "Donations", "INCOME"), cat(2, "Salaries", "EXPENSE"), cat(3, "Food & hostel", "EXPENSE")));
        WebProject reb = new WebProject();
        reb.setId(7L);
        reb.setName("Rahbar-e-Banaat");
        when(projects.findAll()).thenReturn(List.of(reb));
        when(entries.findByBookAndEntryDateBetweenOrderByEntryDateDescIdDesc(eq("RAHBAR"), any(), any())).thenReturn(List.of(
                entry("INCOME", 1, null, "50000"), entry("EXPENSE", 2, 7L, "20000"), entry("EXPENSE", 3, 7L, "5000"), entry("EXPENSE", 2, null, "8000")));
        Payment paid = new Payment();
        paid.setAmount(new BigDecimal("12000"));
        paid.setPaymentDate(LocalDateTime.of(2026, 5, 1, 10, 0));
        Payment lastYear = new Payment();
        lastYear.setAmount(new BigDecimal("999"));
        lastYear.setPaymentDate(LocalDateTime.of(2025, 5, 1, 10, 0));
        when(sponsorPayments.findByStatusIgnoreCaseOrderByPaymentDateDesc("Paid")).thenReturn(List.of(paid, lastYear));
        RebPayment fee = new RebPayment();
        fee.setAmount(new BigDecimal("1500"));
        fee.setStatus("VALID");
        RebPayment cancelled = new RebPayment();
        cancelled.setAmount(new BigDecimal("700"));
        cancelled.setStatus("CANCELLED");
        when(rebPayments.findByPaidOnBetweenOrderByPaidOnAscIdAsc(any(), any())).thenReturn(List.of(fee, cancelled));

        Map<String, Object> s = service.summary("RAHBAR", 2026);
        assertEquals(new BigDecimal("50000"), s.get("income"));
        assertEquals(new BigDecimal("33000"), s.get("expense"));
        assertEquals(new BigDecimal("12000"), s.get("scholarships_paid"));
        assertFalse(s.containsKey("reb_fees_collected")); // REB's money stays in REB's book
        verify(entries, never()).findByBookAndEntryDateBetweenOrderByEntryDateDescIdDesc(eq("REB"), any(), any());

        Map<String, Object> rebBook = service.summary("REB", 2026);
        assertEquals(new BigDecimal("1500"), rebBook.get("reb_fees_collected"));
        assertFalse(rebBook.containsKey("scholarships_paid"));
        assertEquals(BigDecimal.ZERO, rebBook.get("income")); // none of Rahbar's entries
        @SuppressWarnings("unchecked") List<Map<String, Object>> byProject = (List<Map<String, Object>>) s.get("expense_by_project");
        assertEquals("Rahbar-e-Banaat", byProject.get(0).get("name"));
        assertEquals(new BigDecimal("25000"), byProject.get(0).get("amount"));
        assertFalse(s.toString().contains("Somebody private")); // no payees in the summary
    }

    @Test
    void theWebsiteOnlyGetsPublishedYears() {
        when(published.existsByBookAndYear("RAHBAR", 2025)).thenReturn(true);
        when(published.existsByBookAndYear("REB", 2025)).thenReturn(false);
        assertThrows(ApiException.class, () -> service.publicSummary("REB", 2025)); // each book publishes its own years
    }

    @Test
    void anEntryTakesItsKindFromTheCategory() {
        when(categories.findByIdAndBook(2L, "RAHBAR")).thenReturn(Optional.of(cat(2, "Salaries", "EXPENSE")));
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
        FinEntry e = service.saveEntry("RAHBAR", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 2, "amount", "1000", "kind", "INCOME")));
        assertEquals("EXPENSE", e.getKind());
        assertEquals("RAHBAR", e.getBook());
        assertThrows(ApiException.class, () -> service.saveEntry("RAHBAR", null, new HashMap<>(Map.of("entryDate", LocalDate.now().plusDays(1).toString(), "categoryId", 2, "amount", "1"))));
        // A Rahbar category can't be used in REB's book (and the other way round).
        assertThrows(ApiException.class, () -> service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 2, "amount", "1000"))));
    }
}
