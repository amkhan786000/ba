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
    private final RebPaymentRepository rebPayments = mock(RebPaymentRepository.class);
    private final FinPayeeRepository payees = mock(FinPayeeRepository.class);
    private final ChapterRepository chapters = mock(ChapterRepository.class);
    private final FinanceService service = new FinanceService(categories, entries, published, projects, rebPayments,
            mock(UserRepository.class), payees, chapters);

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
    void rebsSummaryCountsFeesAndTheWebsiteGetsTotalsOnly() {
        when(categories.findByBookOrderByKindAscNameAsc("REB")).thenReturn(List.of(cat(1, "Donations", "INCOME"), cat(2, "Salaries", "EXPENSE"), cat(3, "Food & hostel", "EXPENSE")));
        WebProject reb = new WebProject();
        reb.setId(7L);
        reb.setName("Rahbar-e-Banaat");
        when(projects.findAll()).thenReturn(List.of(reb));
        when(entries.findByBookAndEntryDateBetweenOrderByEntryDateDescIdDesc(eq("REB"), any(), any())).thenReturn(List.of(
                entry("INCOME", 1, null, "50000"), entry("EXPENSE", 2, 7L, "20000"), entry("EXPENSE", 3, 7L, "5000"), entry("EXPENSE", 2, null, "8000")));
        RebPayment fee = new RebPayment();
        fee.setAmount(new BigDecimal("1500"));
        fee.setStatus("VALID");
        RebPayment cancelled = new RebPayment();
        cancelled.setAmount(new BigDecimal("700"));
        cancelled.setStatus("CANCELLED");
        when(rebPayments.findByPaidOnBetweenOrderByPaidOnAscIdAsc(any(), any())).thenReturn(List.of(fee, cancelled));

        Map<String, Object> s = service.summary("REB", 2026);
        assertEquals(new BigDecimal("50000"), s.get("income"));
        assertEquals(new BigDecimal("33000"), s.get("expense"));
        assertEquals(new BigDecimal("1500"), s.get("reb_fees_collected"));
        assertFalse(s.containsKey("scholarships_paid")); // sponsorship money is never in a book
        @SuppressWarnings("unchecked") List<Map<String, Object>> byProject = (List<Map<String, Object>>) s.get("expense_by_project");
        assertEquals("Rahbar-e-Banaat", byProject.get(0).get("name"));
        assertEquals(new BigDecimal("25000"), byProject.get(0).get("amount"));
        // The book's own summary says who was paid; the website's never does.
        assertTrue(s.toString().contains("Somebody private"));
        when(published.existsByBookAndYear("REB", 2026)).thenReturn(true);
        Map<String, Object> web = service.publicSummary("REB", 2026);
        assertFalse(web.toString().contains("Somebody private"));
        assertFalse(web.containsKey("expense_by_payee"));
        assertFalse(web.containsKey("income_by_chapter"));
    }

    @Test
    void rahbarsOwnBookIsGone() {
        assertEquals("REB", FinanceService.book("reb"));
        assertThrows(ApiException.class, () -> FinanceService.book("rahbar")); // removed with its data (V48)
    }

    @Test
    void theWebsiteOnlyGetsPublishedYears() {
        when(published.existsByBookAndYear("REB", 2025)).thenReturn(false);
        assertThrows(ApiException.class, () -> service.publicSummary("REB", 2025));
    }

    @Test
    void anEntryTakesItsKindFromTheCategory() {
        when(categories.findByIdAndBook(2L, "REB")).thenReturn(Optional.of(cat(2, "Salaries", "EXPENSE")));
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
        FinEntry e = service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 2, "amount", "1000", "kind", "INCOME")));
        assertEquals("EXPENSE", e.getKind());
        assertEquals("REB", e.getBook());
        assertThrows(ApiException.class, () -> service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", LocalDate.now().plusDays(1).toString(), "categoryId", 2, "amount", "1"))));
        // A category that isn't REB's is refused.
        assertThrows(ApiException.class, () -> service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 5, "amount", "1000"))));
    }

    @Test
    void incomeCanComeFromAChapterOrSomeoneTypedInAndExpensesGoToThePaidToList() {
        when(categories.findByIdAndBook(1L, "REB")).thenReturn(Optional.of(cat(1, "Donations", "INCOME")));
        when(categories.findByIdAndBook(2L, "REB")).thenReturn(Optional.of(cat(2, "Rent", "EXPENSE")));
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
        Chapter jeddah = new Chapter();
        jeddah.setChapterId(4L);
        jeddah.setChapterName("Jeddah Chapter");
        when(chapters.findById(4L)).thenReturn(Optional.of(jeddah));
        FinPayee landlord = new FinPayee();
        landlord.setId(9L);
        landlord.setBook("REB");
        landlord.setName("Hostel landlord");
        when(payees.findByIdAndBook(9L, "REB")).thenReturn(Optional.of(landlord));

        FinEntry in = service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 1, "amount", "5000",
                "chapterId", 4, "party", "ignored")));
        assertEquals(4L, in.getChapterId());
        assertEquals("Jeddah Chapter", in.getParty());

        FinEntry out = service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 2, "amount", "9000",
                "payeeId", 9, "chapterId", 4)));
        assertEquals(9L, out.getPayeeId());
        assertNull(out.getChapterId()); // an expense is never "received from" a chapter
        assertEquals("Hostel landlord", out.getParty());

        // Someone not on the list is refused; income may come from someone typed in.
        assertThrows(ApiException.class, () -> service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 2,
                "amount", "1", "payeeId", 8))));
        FinEntry typed = service.saveEntry("REB", null, new HashMap<>(Map.of("entryDate", "2026-03-01", "categoryId", 1, "amount", "100",
                "party", "A well-wisher")));
        assertEquals("A well-wisher", typed.getParty());
        assertNull(typed.getChapterId());
    }
}
