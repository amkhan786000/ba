package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * Administration > Income & Expenses: the organisation's money in and out (donations, zakat, salaries, rent, ...)
 * by category and, optionally, by website project. The yearly summary also counts what Rahbar records elsewhere:
 * sponsors' scholarship payments to students (Payment Records, status Paid) and REB fees collected. A year's summary
 * (totals only, no names) can be published on biharanjuman.com ("where the money went").
 */
@Service
public class FinanceService {

    static final Set<String> KINDS = Set.of("INCOME", "EXPENSE");
    static final Set<String> METHODS = Set.of("CASH", "UPI", "BANK", "CHEQUE");

    private final FinCategoryRepository categories;
    private final FinEntryRepository entries;
    private final FinPublishedYearRepository published;
    private final WebProjectRepository projects;
    private final PaymentRepository sponsorPayments;
    private final RebPaymentRepository rebPayments;
    private final UserRepository users;

    public FinanceService(FinCategoryRepository categories, FinEntryRepository entries, FinPublishedYearRepository published,
                          WebProjectRepository projects, PaymentRepository sponsorPayments, RebPaymentRepository rebPayments,
                          UserRepository users) {
        this.categories = categories;
        this.entries = entries;
        this.published = published;
        this.projects = projects;
        this.sponsorPayments = sponsorPayments;
        this.rebPayments = rebPayments;
        this.users = users;
    }

    // ---------------------------------------------------------------- categories

    public List<Map<String, Object>> categories() {
        return Rows.list(categories.findAllByOrderByKindAscNameAsc());
    }

    /** Body: { name, kind INCOME | EXPENSE, active }. */
    @Transactional
    public FinCategory saveCategory(Long id, Map<String, Object> body) {
        FinCategory c = id == null ? new FinCategory() : categories.findById(id).orElseThrow(() -> notFound("Category"));
        if (id == null) c.setKind(code(body.get("kind"), KINDS, null, "kind"));
        if (c.getKind() == null) throw bad("Choose income or expense.");
        c.setName(text(body.get("name"), "Name", 100, true));
        if (id == null ? categories.existsByKindAndNameIgnoreCase(c.getKind(), c.getName())
                : categories.existsByKindAndNameIgnoreCaseAndIdNot(c.getKind(), c.getName(), id)) {
            throw bad("There is already a category called " + c.getName() + ".");
        }
        c.setActive(flag(body.get("active"), true));
        return categories.save(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        FinCategory c = categories.findById(id).orElseThrow(() -> notFound("Category"));
        if (entries.existsByCategoryId(id)) throw bad(c.getName() + " has entries; hide it instead (untick Active).");
        categories.delete(c);
    }

    /** Website projects an entry can belong to. */
    public List<Map<String, Object>> projectOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        projects.findAll().stream().sorted(Comparator.comparing(WebProject::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> out.add(Map.of("id", p.getId(), "name", p.getName())));
        return out;
    }

    // ---------------------------------------------------------------- entries

    /** Entries between two days (default: this year), filtered by kind, category and project. */
    public Map<String, Object> entries(LocalDate from, LocalDate to, String kind, Long categoryId, Long projectId) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfYear(1);
        LocalDate end = to != null ? to : LocalDate.now().withMonth(12).withDayOfMonth(31);
        if (end.isBefore(start)) throw bad("The end date is before the start date.");
        Map<Long, String> catNames = new HashMap<>();
        categories.findAll().forEach(c -> catNames.put(c.getId(), c.getName()));
        Map<Long, String> projectNames = new HashMap<>();
        projects.findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        List<FinEntry> list = entries.findByEntryDateBetweenOrderByEntryDateDescIdDesc(start, end);
        Map<Long, String> who = new HashMap<>();
        users.findAllById(list.stream().map(FinEntry::getCreatedBy).filter(Objects::nonNull).distinct().toList()).forEach(u -> who.put(u.getId(), u.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (FinEntry e : list) {
            if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase(e.getKind())) continue;
            if (categoryId != null && !categoryId.equals(e.getCategoryId())) continue;
            if (projectId != null && !projectId.equals(e.getProjectId())) continue;
            Map<String, Object> r = Rows.of(e);
            r.put("category_name", catNames.get(e.getCategoryId()));
            r.put("project_name", projectNames.get(e.getProjectId()));
            r.put("entered_by", who.get(e.getCreatedBy()));
            rows.add(r);
            if ("INCOME".equals(e.getKind())) income = income.add(e.getAmount()); else expense = expense.add(e.getAmount());
        }
        return Map.of("from", start, "to", end, "entries", rows, "income", income, "expense", expense);
    }

    /** Body: { entryDate, kind, categoryId, projectId, amount, party, description, method, reference }. */
    @Transactional
    public FinEntry saveEntry(Long id, Map<String, Object> body) {
        FinEntry e = id == null ? new FinEntry() : entries.findById(id).orElseThrow(() -> notFound("Entry"));
        e.setEntryDate(date(body.get("entryDate"), "Date"));
        if (e.getEntryDate() == null) throw bad("Enter the date.");
        if (e.getEntryDate().isAfter(LocalDate.now())) throw bad("The date is in the future.");
        FinCategory c = categories.findById(Objects.requireNonNullElse(id(body.get("categoryId")), -1L)).orElseThrow(() -> bad("Choose the category."));
        e.setKind(c.getKind());
        e.setCategoryId(c.getId());
        Long project = id(body.get("projectId"));
        if (project != null && !projects.existsById(project)) throw bad("Unknown project.");
        e.setProjectId(project);
        BigDecimal amount = money(body.get("amount"), "Amount");
        if (amount == null || amount.signum() <= 0) throw bad("Enter the amount.");
        e.setAmount(amount);
        e.setParty(text(body.get("party"), "INCOME".equals(c.getKind()) ? "Received from" : "Paid to", 150, false));
        e.setDescription(text(body.get("description"), "Description", 500, false));
        e.setMethod(code(body.get("method"), METHODS, "BANK", "payment method"));
        e.setReference(text(body.get("reference"), "Reference", 100, false));
        return entries.save(e);
    }

    @Transactional
    public void deleteEntry(Long id) {
        entries.delete(entries.findById(id).orElseThrow(() -> notFound("Entry")));
    }

    // ---------------------------------------------------------------- summary

    /**
     * A year's summary: income and expenses by category and by project, plus the scholarships sponsors paid to
     * students through Rahbar and the REB fees collected. names: false for the website (no parties anyway).
     */
    public Map<String, Object> summary(int year) {
        LocalDate start = LocalDate.of(year, 1, 1), end = LocalDate.of(year, 12, 31);
        Map<Long, FinCategory> cats = new HashMap<>();
        categories.findAll().forEach(c -> cats.put(c.getId(), c));
        Map<Long, String> projectNames = new HashMap<>();
        projects.findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        Map<String, BigDecimal> incomeBy = new TreeMap<>(), expenseBy = new TreeMap<>(), projectSpend = new TreeMap<>();
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO;
        for (FinEntry e : entries.findByEntryDateBetweenOrderByEntryDateDescIdDesc(start, end)) {
            String cat = cats.containsKey(e.getCategoryId()) ? cats.get(e.getCategoryId()).getName() : "Other";
            if ("INCOME".equals(e.getKind())) {
                incomeBy.merge(cat, e.getAmount(), BigDecimal::add);
                income = income.add(e.getAmount());
            } else {
                expenseBy.merge(cat, e.getAmount(), BigDecimal::add);
                expense = expense.add(e.getAmount());
                projectSpend.merge(e.getProjectId() == null ? "General (the organisation)" : projectNames.getOrDefault(e.getProjectId(), "Other project"),
                        e.getAmount(), BigDecimal::add);
            }
        }
        BigDecimal scholarships = BigDecimal.ZERO;
        for (Payment p : sponsorPayments.findByStatusIgnoreCaseOrderByPaymentDateDesc("Paid")) {
            if (p.getPaymentDate() != null && p.getPaymentDate().getYear() == year && p.getAmount() != null) scholarships = scholarships.add(p.getAmount());
        }
        BigDecimal rebFees = BigDecimal.ZERO;
        for (RebPayment p : rebPayments.findByPaidOnBetweenOrderByPaidOnAscIdAsc(start, end)) {
            if ("VALID".equals(p.getStatus())) rebFees = rebFees.add(p.getAmount());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("income_by_category", rows(incomeBy));
        out.put("expense_by_category", rows(expenseBy));
        out.put("expense_by_project", rows(projectSpend));
        out.put("income", income);
        out.put("expense", expense);
        out.put("scholarships_paid", scholarships);
        out.put("reb_fees_collected", rebFees);
        out.put("published", published.existsById(year));
        published.findById(year).ifPresent(p -> out.put("note", p.getNote()));
        return out;
    }

    private static List<Map<String, Object>> rows(Map<String, BigDecimal> m) {
        List<Map<String, Object>> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(Map.of("name", k, "amount", v)));
        out.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("amount")).reversed());
        return out;
    }

    // ---------------------------------------------------------------- website

    public List<Map<String, Object>> publishedYears() {
        return Rows.list(published.findAllByOrderByYearDesc());
    }

    /** Body: { year, note }: shows that year's summary on the website. */
    @Transactional
    public void publish(Map<String, Object> body) {
        Integer year = number(body.get("year"), "Year", 2000, LocalDate.now().getYear());
        if (year == null) throw bad("Choose the year.");
        FinPublishedYear p = published.findById(year).orElseGet(() -> {
            FinPublishedYear n = new FinPublishedYear();
            n.setYear(year);
            return n;
        });
        p.setNote(text(body.get("note"), "Note", 500, false));
        published.save(p);
    }

    @Transactional
    public void unpublish(int year) {
        published.findById(year).ifPresent(published::delete);
    }

    /** For the website: published years, newest first. */
    public List<Integer> publicYears() {
        return published.findAllByOrderByYearDesc().stream().map(FinPublishedYear::getYear).toList();
    }

    /** For the website: a published year's totals (404 when the year isn't published). */
    public Map<String, Object> publicSummary(int year) {
        if (!published.existsById(year)) throw new ApiException(HttpStatus.NOT_FOUND, "Not published.");
        Map<String, Object> s = summary(year);
        s.remove("published");
        return s;
    }
}
