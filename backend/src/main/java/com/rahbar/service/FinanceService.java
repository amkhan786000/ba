package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Paging;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB's book of income & expenses (donations, zakat, salaries, rent, ...) by category and, optionally, by website
 * project: Rahbar-e-Banaat > REB Income & Expenses (permission REB_FINANCE). Its yearly summary also counts the REB
 * fees collected, and can be published on biharanjuman.com (totals only, no names). The tables keep a book column:
 * Rahbar's own book was removed with its data (V48), so REB is the only book.
 */
@Service
public class FinanceService {

    public static final String REB = "REB";
    static final Set<String> BOOKS = Set.of(REB);
    static final Set<String> KINDS = Set.of("INCOME", "EXPENSE");
    static final Set<String> METHODS = Set.of("CASH", "UPI", "BANK", "CHEQUE");

    private final FinCategoryRepository categories;
    private final FinEntryRepository entries;
    private final FinPublishedYearRepository published;
    private final WebProjectRepository projects;
    private final RebPaymentRepository rebPayments;
    private final UserRepository users;
    private final FinPayeeRepository payees;
    private final ChapterRepository chapters;

    public FinanceService(FinCategoryRepository categories, FinEntryRepository entries, FinPublishedYearRepository published,
                          WebProjectRepository projects, RebPaymentRepository rebPayments,
                          UserRepository users, FinPayeeRepository payees, ChapterRepository chapters) {
        this.payees = payees;
        this.chapters = chapters;
        this.categories = categories;
        this.entries = entries;
        this.published = published;
        this.projects = projects;
        this.rebPayments = rebPayments;
        this.users = users;
    }

    // ---------------------------------------------------------------- categories

    /** REB (from the address, /books/reb/...); anything else, including Rahbar's removed book, is not found. */
    public static String book(String value) {
        String b = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!BOOKS.contains(b)) throw new ApiException(HttpStatus.NOT_FOUND, "Unknown book.");
        return b;
    }

    public List<Map<String, Object>> categories(String book) {
        return Rows.list(categories.findByBookOrderByKindAscNameAsc(book));
    }

    /** Body: { name, kind INCOME | EXPENSE, active }. */
    @Transactional
    public FinCategory saveCategory(String book, Long id, Map<String, Object> body) {
        FinCategory c = id == null ? new FinCategory() : categories.findByIdAndBook(id, book).orElseThrow(() -> notFound("Category"));
        c.setBook(book);
        if (id == null) c.setKind(code(body.get("kind"), KINDS, null, "kind"));
        if (c.getKind() == null) throw bad("Choose income or expense.");
        c.setName(text(body.get("name"), "Name", 100, true));
        if (id == null ? categories.existsByBookAndKindAndNameIgnoreCase(book, c.getKind(), c.getName())
                : categories.existsByBookAndKindAndNameIgnoreCaseAndIdNot(book, c.getKind(), c.getName(), id)) {
            throw bad("There is already a category called " + c.getName() + ".");
        }
        c.setActive(flag(body.get("active"), true));
        return categories.save(c);
    }

    @Transactional
    public void deleteCategory(String book, Long id) {
        FinCategory c = categories.findByIdAndBook(id, book).orElseThrow(() -> notFound("Category"));
        if (entries.existsByCategoryId(id)) throw bad(c.getName() + " has entries; hide it instead (untick Active).");
        categories.delete(c);
    }

    // ---------------------------------------------------------------- "paid to" list and chapters

    /** The book's "Paid to" list (Income & Expenses > Paid to). */
    public List<Map<String, Object>> payees(String book) {
        return Rows.list(payees.findByBookOrderByNameAsc(book));
    }

    /** Body: { name, phone, note, active }. */
    @Transactional
    public FinPayee savePayee(String book, Long id, Map<String, Object> body) {
        FinPayee p = id == null ? new FinPayee() : payees.findByIdAndBook(id, book).orElseThrow(() -> notFound("Payee"));
        p.setBook(book);
        p.setName(text(body.get("name"), "Name", 150, true));
        if (id == null ? payees.existsByBookAndNameIgnoreCase(book, p.getName())
                : payees.existsByBookAndNameIgnoreCaseAndIdNot(book, p.getName(), id)) {
            throw bad(p.getName() + " is already on the list.");
        }
        p.setPhone(text(body.get("phone"), "Phone", 30, false));
        p.setNote(text(body.get("note"), "Note", 300, false));
        p.setActive(flag(body.get("active"), true));
        FinPayee saved = payees.save(p);
        // A new name: entries already paid to it keep their text; a renamed one shows the new name from now on.
        if (id != null) {
            entries.findAll().stream().filter(e -> id.equals(e.getPayeeId())).forEach(e -> { e.setParty(saved.getName()); entries.save(e); });
        }
        return saved;
    }

    @Transactional
    public void deletePayee(String book, Long id) {
        FinPayee p = payees.findByIdAndBook(id, book).orElseThrow(() -> notFound("Payee"));
        if (entries.existsByPayeeId(id)) throw bad(p.getName() + " has entries; hide it instead (untick Active).");
        payees.delete(p);
    }

    /** Chapters income can be received from. */
    public List<Map<String, Object>> chapterOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        chapters.findAllByOrderByChapterNameAsc().forEach(c -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", c.getChapterId());
            r.put("name", c.getChapterName());
            r.put("active", !Boolean.FALSE.equals(c.getActive()));
            out.add(r);
        });
        return out;
    }

    /** Website projects an entry can belong to. */
    public List<Map<String, Object>> projectOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        projects.findAll().stream().sorted(Comparator.comparing(WebProject::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> out.add(Map.of("id", p.getId(), "name", p.getName())));
        return out;
    }

    // ---------------------------------------------------------------- entries

    /**
     * One page of the entries between two days (default: this year), filtered by kind, category, project and text
     * (party, description, reference, category, project), with the income and expense totals of all that match.
     */
    public Map<String, Object> entries(String book, LocalDate from, LocalDate to, String kind, Long categoryId, Long projectId,
                                       String q, Integer page, Integer size) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : LocalDate.now().withMonth(12).withDayOfMonth(31);
        if (end.isBefore(start)) throw bad("The end date is before the start date.");
        Map<Long, String> catNames = new HashMap<>();
        categories.findByBookOrderByKindAscNameAsc(book).forEach(c -> catNames.put(c.getId(), c.getName()));
        Map<Long, String> projectNames = new HashMap<>();
        projects.findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        List<FinEntry> filtered = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (FinEntry e : entries.findByBookAndEntryDateBetweenOrderByEntryDateDescIdDesc(book, start, end)) {
            if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase(e.getKind())) continue;
            if (categoryId != null && !categoryId.equals(e.getCategoryId())) continue;
            if (projectId != null && !projectId.equals(e.getProjectId())) continue;
            if (!Paging.matches(q, e.getParty(), e.getDescription(), e.getReference(), catNames.get(e.getCategoryId()), projectNames.get(e.getProjectId()))) continue;
            filtered.add(e);
            if ("INCOME".equals(e.getKind())) income = income.add(e.getAmount()); else expense = expense.add(e.getAmount());
        }
        Map<String, Object> body = Paging.of(filtered, page, size, slice -> {
            Map<Long, String> who = new HashMap<>();
            users.findAllById(slice.stream().map(FinEntry::getCreatedBy).filter(Objects::nonNull).distinct().toList()).forEach(u -> who.put(u.getId(), u.getName()));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (FinEntry e : slice) {
                Map<String, Object> r = Rows.of(e);
                r.put("category_name", catNames.get(e.getCategoryId()));
                r.put("project_name", projectNames.get(e.getProjectId()));
                r.put("entered_by", who.get(e.getCreatedBy()));
                rows.add(r);
            }
            return rows;
        });
        body.put("from", start);
        body.put("to", end);
        body.put("income", income);
        body.put("expense", expense);
        return body;
    }

    /** Body: { entryDate, kind, categoryId, projectId, amount, party, description, method, reference }. */
    @Transactional
    public FinEntry saveEntry(String book, Long id, Map<String, Object> body) {
        FinEntry e = id == null ? new FinEntry() : entries.findByIdAndBook(id, book).orElseThrow(() -> notFound("Entry"));
        e.setBook(book);
        e.setEntryDate(date(body.get("entryDate"), "Date"));
        if (e.getEntryDate() == null) throw bad("Enter the date.");
        if (e.getEntryDate().isAfter(LocalDate.now())) throw bad("The date is in the future.");
        FinCategory c = categories.findByIdAndBook(Objects.requireNonNullElse(id(body.get("categoryId")), -1L), book)
                .orElseThrow(() -> bad("Choose the category."));
        e.setKind(c.getKind());
        e.setCategoryId(c.getId());
        Long project = id(body.get("projectId"));
        if (project != null && !projects.existsById(project)) throw bad("Unknown project.");
        e.setProjectId(project);
        BigDecimal amount = money(body.get("amount"), "Amount");
        if (amount == null || amount.signum() <= 0) throw bad("Enter the amount.");
        e.setAmount(amount);
        // Received from a chapter / paid to someone on the list; otherwise the name typed in. (Website donations are
        // booked by DonationBooks with the donor's name.)
        Long chapterId = "INCOME".equals(c.getKind()) ? id(body.get("chapterId")) : null;
        Long payeeId = "EXPENSE".equals(c.getKind()) ? id(body.get("payeeId")) : null;
        e.setChapterId(null);
        e.setPayeeId(null);
        if (chapterId != null) {
            Chapter ch = chapters.findById(chapterId).orElseThrow(() -> bad("Unknown chapter."));
            e.setChapterId(ch.getChapterId());
            e.setParty(ch.getChapterName());
        } else if (payeeId != null) {
            FinPayee p = payees.findByIdAndBook(payeeId, book).orElseThrow(() -> bad("Choose who was paid from this book's list."));
            e.setPayeeId(p.getId());
            e.setParty(p.getName());
        } else {
            e.setParty(text(body.get("party"), "INCOME".equals(c.getKind()) ? "Received from" : "Paid to", 150, false));
        }
        e.setDescription(text(body.get("description"), "Description", 500, false));
        e.setMethod(code(body.get("method"), METHODS, "BANK", "payment method"));
        e.setReference(text(body.get("reference"), "Reference", 100, false));
        return entries.save(e);
    }

    @Transactional
    public void deleteEntry(String book, Long id) {
        entries.delete(entries.findByIdAndBook(id, book).orElseThrow(() -> notFound("Entry")));
    }

    // ---------------------------------------------------------------- balance

    /**
     * The book's money at a glance (entries recorded here only): received, spent and the balance in hand since the
     * first entry, the same for this calendar year and this month, and the last 12 months for a chart.
     */
    public Map<String, Object> balance(String book) {
        LocalDate today = LocalDate.now();
        BigDecimal in = entries.totalUpTo(book, "INCOME", today), out = entries.totalUpTo(book, "EXPENSE", today);
        LocalDate yearStart = today.withDayOfYear(1), monthStart = today.withDayOfMonth(1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("received", in);
        body.put("spent", out);
        body.put("balance", in.subtract(out));
        body.put("receivedThisYear", entries.totalBetween(book, "INCOME", yearStart, today));
        body.put("spentThisYear", entries.totalBetween(book, "EXPENSE", yearStart, today));
        body.put("receivedThisMonth", entries.totalBetween(book, "INCOME", monthStart, today));
        body.put("spentThisMonth", entries.totalBetween(book, "EXPENSE", monthStart, today));
        body.put("year", today.getYear());
        body.put("book", book);
        body.put("lastEntry", entries.findFirstByBookOrderByEntryDateDescIdDesc(book).map(FinEntry::getEntryDate).orElse(null));
        // Last 12 months: received, spent and the balance at the end of each month.
        List<String> labels = new ArrayList<>();
        List<BigDecimal> received = new ArrayList<>(), spent = new ArrayList<>(), closing = new ArrayList<>();
        java.time.YearMonth first = java.time.YearMonth.from(today).minusMonths(11);
        for (int i = 0; i < 12; i++) {
            java.time.YearMonth m = first.plusMonths(i);
            LocalDate from = m.atDay(1), to = m.atEndOfMonth().isAfter(today) ? today : m.atEndOfMonth();
            labels.add(m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + String.valueOf(m.getYear()).substring(2));
            received.add(entries.totalBetween(book, "INCOME", from, to));
            spent.add(entries.totalBetween(book, "EXPENSE", from, to));
            closing.add(entries.totalUpTo(book, "INCOME", to).subtract(entries.totalUpTo(book, "EXPENSE", to)));
        }
        body.put("months", Map.of("labels", labels, "series", List.of(
                Map.of("name", "Received", "values", received), Map.of("name", "Spent", "values", spent))));
        body.put("closingBalance", closing);
        return body;
    }

    // ---------------------------------------------------------------- summary

    /**
     * A year's summary: income and expenses by category and by project, plus the REB fees collected.
     */
    public Map<String, Object> summary(String book, int year) {
        LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
        Map<Long, FinCategory> cats = new HashMap<>();
        categories.findByBookOrderByKindAscNameAsc(book).forEach(c -> cats.put(c.getId(), c));
        Map<Long, String> projectNames = new HashMap<>();
        projects.findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        Map<String, BigDecimal> incomeBy = new TreeMap<>(), expenseBy = new TreeMap<>(), projectSpend = new TreeMap<>();
        Map<String, BigDecimal> byChapter = new TreeMap<>(), byPayee = new TreeMap<>();
        Map<Long, String> chapterNames = new HashMap<>();
        chapters.findAll().forEach(ch -> chapterNames.put(ch.getChapterId(), ch.getChapterName()));
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (FinEntry e : entries.findByBookAndEntryDateBetweenOrderByEntryDateDescIdDesc(book, start, end)) {
            String cat = cats.containsKey(e.getCategoryId()) ? cats.get(e.getCategoryId()).getName() : "Other";
            if ("INCOME".equals(e.getKind())) {
                incomeBy.merge(cat, e.getAmount(), BigDecimal::add);
                income = income.add(e.getAmount());
                byChapter.merge(e.getChapterId() == null ? "Others (not a chapter)" : chapterNames.getOrDefault(e.getChapterId(), "Other chapter"),
                        e.getAmount(), BigDecimal::add);
            } else {
                expenseBy.merge(cat, e.getAmount(), BigDecimal::add);
                expense = expense.add(e.getAmount());
                String who = e.getParty() == null || e.getParty().isBlank() ? "Not recorded" : e.getParty().trim();
                byPayee.merge(who, e.getAmount(), BigDecimal::add);
                projectSpend.merge(e.getProjectId() == null ? "General (the organisation)" : projectNames.getOrDefault(e.getProjectId(), "Other project"),
                        e.getAmount(), BigDecimal::add);
            }
        }
        // The REB fees collected in the year (REB Fees screens).
        BigDecimal rebFees = BigDecimal.ZERO;
        for (RebPayment p : rebPayments.findByPaidOnBetweenOrderByPaidOnAscIdAsc(start, end)) {
            if ("VALID".equals(p.getStatus())) rebFees = rebFees.add(p.getAmount());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("income_by_category", rows(incomeBy));
        out.put("expense_by_category", rows(expenseBy));
        out.put("expense_by_project", rows(projectSpend));
        out.put("income_by_chapter", rows(byChapter));
        out.put("expense_by_payee", rows(byPayee));
        out.put("income", income);
        out.put("expense", expense);
        out.put("book", book);
        out.put("reb_fees_collected", rebFees);
        out.put("published", published.existsByBookAndYear(book, year));
        published.findByBookAndYear(book, year).ifPresent(p -> out.put("note", p.getNote()));
        return out;
    }

    private static List<Map<String, Object>> rows(Map<String, BigDecimal> m) {
        List<Map<String, Object>> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(Map.of("name", k, "amount", v)));
        out.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("amount")).reversed());
        return out;
    }

    // ---------------------------------------------------------------- website

    public List<Map<String, Object>> publishedYears(String book) {
        return Rows.list(published.findByBookOrderByYearDesc(book));
    }

    /** Body: { year, note }: shows that year's summary on the website. */
    @Transactional
    public void publish(String book, Map<String, Object> body) {
        Integer year = number(body.get("year"), "Year", 2000, LocalDate.now().getYear());
        if (year == null) throw bad("Choose the year.");
        FinPublishedYear p = published.findByBookAndYear(book, year).orElseGet(() -> {
            FinPublishedYear n = new FinPublishedYear();
            n.setBook(book);
            n.setYear(year);
            return n;
        });
        p.setNote(text(body.get("note"), "Note", 500, false));
        published.save(p);
    }

    @Transactional
    public void unpublish(String book, int year) {
        published.findByBookAndYear(book, year).ifPresent(published::delete);
    }

    /** For the website: a book's published years, newest first. */
    public List<Integer> publicYears(String book) {
        return published.findByBookOrderByYearDesc(book).stream().map(FinPublishedYear::getYear).toList();
    }

    /** For the website: a published year's totals of one book (404 when that year isn't published). */
    public Map<String, Object> publicSummary(String book, int year) {
        if (!published.existsByBookAndYear(book, year)) throw new ApiException(HttpStatus.NOT_FOUND, "Not published.");
        Map<String, Object> s = summary(book, year);
        s.remove("published");
        s.remove("expense_by_payee"); // names stay in Rahbar: the website shows totals only
        s.remove("income_by_chapter");
        return s;
    }
}
