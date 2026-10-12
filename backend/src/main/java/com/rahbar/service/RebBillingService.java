package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB bills and payments. Once a month the office makes the bills: one per girl studying in the month's session,
 * from her class's fee structure (monthly heads; annual heads in their month; one-time heads in her first bill;
 * hostel heads only for residential girls), minus concessions; a sponsorship moves its monthly amount to the sponsor
 * share. Making bills again only adds the missing ones. Payments fill the oldest unpaid bills of the payer's share
 * (or the bills chosen) and get a receipt number; they are cancelled, never deleted.
 */
@Service
public class RebBillingService {

    static final Set<String> METHODS = Set.of("CASH", "UPI", "BANK", "CHEQUE");
    static final Set<String> OPEN = Set.of("DUE", "PARTIAL");

    private final RebInvoiceRepository invoices;
    private final RebInvoiceLineRepository lines;
    private final RebPaymentRepository payments;
    private final RebPaymentAllocationRepository allocations;
    private final RebFeeStructureRepository structures;
    private final RebFeeHeadRepository heads;
    private final RebConcessionRepository concessions;
    private final RebStudentRepository students;
    private final RebEnrollmentRepository enrollments;
    private final RebSectionRepository sections;
    private final RebSessionRepository sessions;
    private final RebSetupService setup;
    private final RebStudentService studentService;
    private final RebSponsorshipService sponsorshipService;
    private final com.rahbar.repository.UserRepository users;

    public RebBillingService(RebInvoiceRepository invoices, RebInvoiceLineRepository lines, RebPaymentRepository payments,
                             RebPaymentAllocationRepository allocations, RebFeeStructureRepository structures, RebFeeHeadRepository heads,
                             RebConcessionRepository concessions, RebStudentRepository students, RebEnrollmentRepository enrollments,
                             RebSectionRepository sections, RebSessionRepository sessions, RebSetupService setup,
                             RebStudentService studentService, RebSponsorshipService sponsorshipService,
                             com.rahbar.repository.UserRepository users) {
        this.invoices = invoices;
        this.lines = lines;
        this.payments = payments;
        this.allocations = allocations;
        this.structures = structures;
        this.heads = heads;
        this.concessions = concessions;
        this.students = students;
        this.enrollments = enrollments;
        this.sections = sections;
        this.sessions = sessions;
        this.setup = setup;
        this.studentService = studentService;
        this.sponsorshipService = sponsorshipService;
        this.users = users;
    }

    static BigDecimal money2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** The session whose dates include the month (its first or last day). */
    RebSession sessionOf(String period) {
        YearMonth ym = YearMonth.parse(period);
        for (RebSession s : sessions.findAll()) {
            boolean firstIn = !ym.atDay(1).isBefore(s.getStartDate()) && !ym.atDay(1).isAfter(s.getEndDate());
            boolean lastIn = !ym.atEndOfMonth().isBefore(s.getStartDate()) && !ym.atEndOfMonth().isAfter(s.getEndDate());
            if (firstIn || lastIn) return s;
        }
        throw bad("No session covers " + label(period) + ". Check the session dates in REB Setup.");
    }

    /** "2026-07" -> "July 2026". */
    static String label(String period) {
        YearMonth ym = YearMonth.parse(period);
        return ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }

    // ---------------------------------------------------------------- making the bills

    /** A bill that would be (or was) made, or why a girl was skipped. */
    record Planned(RebStudent student, String section, RebInvoice invoice, List<RebInvoiceLine> lines, String skipped) {}

    /** Preview of the month's bills: totals, and who is skipped and why. Nothing is saved. */
    public Map<String, Object> preview(String period) {
        return summary(plan(RebFeeService.period(period, "Month")), period, false);
    }

    /** Makes the month's missing bills. */
    @Transactional
    public Map<String, Object> generate(Map<String, Object> body) {
        String period = RebFeeService.period(body.get("period"), "Month");
        if (period == null) throw bad("Choose the month.");
        List<Planned> plan = plan(period);
        String prefix = setup.setting("invoice_prefix", "REB-B") + "/" + period + "/";
        int next = nextNumber(invoices.findNumbersStartingWith(prefix), prefix);
        for (Planned p : plan) {
            if (p.skipped() != null) continue;
            RebInvoice inv = p.invoice();
            inv.setInvoiceNo(prefix + String.format("%04d", next++));
            invoices.save(inv);
            for (RebInvoiceLine l : p.lines()) {
                l.setInvoiceId(inv.getId());
                lines.save(l);
            }
        }
        return summary(plan, period, true);
    }

    private Map<String, Object> summary(List<Planned> plan, String period, boolean made) {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, sponsor = BigDecimal.ZERO;
        int count = 0;
        for (Planned p : plan) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", p.student().getId());
            r.put("name", p.student().getName());
            r.put("admission_no", p.student().getAdmissionNo());
            r.put("section", p.section());
            if (p.skipped() != null) {
                r.put("skipped", p.skipped());
            } else {
                r.put("gross", p.invoice().getGross());
                r.put("concession", p.invoice().getConcession());
                r.put("net", p.invoice().getNet());
                r.put("sponsor_share", p.invoice().getSponsorShare());
                r.put("family_share", p.invoice().getFamilyShare());
                total = total.add(p.invoice().getNet());
                sponsor = sponsor.add(p.invoice().getSponsorShare());
                count++;
            }
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("label", label(period));
        out.put("bills", count);
        out.put("total", total);
        out.put("sponsor_total", sponsor);
        out.put("family_total", total.subtract(sponsor));
        out.put("rows", rows);
        if (made) out.put("message", count == 0 ? "No new bills: every girl already has her bill for " + label(period) + "."
                : count + " bills made for " + label(period) + ".");
        return out;
    }

    /** Works out every girl's bill for the month (nothing saved). */
    List<Planned> plan(String period) {
        if (period == null) throw bad("Choose the month.");
        RebSession session = sessionOf(period);
        YearMonth ym = YearMonth.parse(period);
        Map<Long, RebSection> sectionById = new HashMap<>();
        sections.findBySessionId(session.getId()).forEach(s -> sectionById.put(s.getId(), s));
        Map<Long, String> labels = studentService.sectionLabels();
        Map<Long, RebFeeHead> headById = new HashMap<>();
        heads.findAll().forEach(h -> headById.put(h.getId(), h));
        Map<Long, List<RebFeeStructure>> byClass = new HashMap<>();
        structures.findBySessionId(session.getId()).forEach(s -> byClass.computeIfAbsent(s.getClassId(), k -> new ArrayList<>()).add(s));
        Map<Long, List<RebConcession>> concessionsOf = new HashMap<>();
        concessions.findBySessionId(session.getId()).forEach(c -> concessionsOf.computeIfAbsent(c.getStudentId(), k -> new ArrayList<>()).add(c));
        // heads a girl has already been billed for (for one-time heads)
        Map<Long, Set<Long>> billedHeads = new HashMap<>();
        Map<Long, Long> invoiceStudent = new HashMap<>();
        invoices.findAll().forEach(i -> { if (!"CANCELLED".equals(i.getStatus())) invoiceStudent.put(i.getId(), i.getStudentId()); });
        lines.findAll().forEach(l -> {
            Long st = invoiceStudent.get(l.getInvoiceId());
            if (st != null) billedHeads.computeIfAbsent(st, k -> new HashSet<>()).add(l.getFeeHeadId());
        });
        Set<Long> alreadyBilled = new HashSet<>();
        invoices.findByPeriod(period).forEach(i -> { if (!"CANCELLED".equals(i.getStatus())) alreadyBilled.add(i.getStudentId()); });
        int dueDay;
        try {
            dueDay = Math.max(1, Math.min(28, Integer.parseInt(setup.setting("due_day", "10"))));
        } catch (NumberFormatException e) {
            dueDay = 10;
        }
        LocalDate due = ym.atDay(dueDay);

        List<Planned> out = new ArrayList<>();
        for (RebEnrollment e : enrollments.findBySessionId(session.getId())) {
            RebStudent s = students.findById(e.getStudentId()).orElse(null);
            if (s == null) continue;
            String section = labels.get(e.getSectionId());
            if (!"ACTIVE".equals(s.getStatus()) || !"ACTIVE".equals(e.getStatus())) continue; // not studying: not listed
            if (s.getAdmittedOn() != null && s.getAdmittedOn().isAfter(ym.atEndOfMonth())) {
                out.add(new Planned(s, section, null, List.of(), "Admitted after " + label(period)));
                continue;
            }
            if (alreadyBilled.contains(s.getId())) {
                out.add(new Planned(s, section, null, List.of(), "Already has a bill for " + label(period)));
                continue;
            }
            if ("FREE".equals(s.getFeePlan())) {
                out.add(new Planned(s, section, null, List.of(), "Free fee plan"));
                continue;
            }
            RebSection sec = sectionById.get(e.getSectionId());
            List<RebInvoiceLine> billLines = new ArrayList<>();
            for (RebFeeStructure fs : byClass.getOrDefault(sec == null ? -1L : sec.getClassId(), List.of())) {
                RebFeeHead h = headById.get(fs.getFeeHeadId());
                if (h == null || !h.isActive() || fs.getAmount().signum() <= 0) continue;
                if (h.isResidentialOnly() && !s.isResidential()) continue;
                if ("ANNUAL".equals(h.getFrequency()) && (fs.getBillMonth() == null || fs.getBillMonth() != ym.getMonthValue())) continue;
                if ("ONE_TIME".equals(h.getFrequency()) && billedHeads.getOrDefault(s.getId(), Set.of()).contains(h.getId())) continue;
                RebInvoiceLine l = new RebInvoiceLine();
                l.setFeeHeadId(h.getId());
                l.setAmount(money2(fs.getAmount()));
                billLines.add(l);
            }
            if (billLines.isEmpty()) {
                out.add(new Planned(s, section, null, List.of(), "No fees set for her class (REB Fees > Fee setup)"));
                continue;
            }
            // concessions in force this month
            BigDecimal wholeBill = BigDecimal.ZERO;
            for (RebConcession c : concessionsOf.getOrDefault(s.getId(), List.of())) {
                if (c.getFromPeriod() != null && period.compareTo(c.getFromPeriod()) < 0) continue;
                if (c.getToPeriod() != null && period.compareTo(c.getToPeriod()) > 0) continue;
                if (c.getFeeHeadId() == null && "AMOUNT".equals(c.getKind())) {
                    wholeBill = wholeBill.add(c.getValue());
                    continue;
                }
                for (RebInvoiceLine l : billLines) {
                    if (c.getFeeHeadId() != null && !c.getFeeHeadId().equals(l.getFeeHeadId())) continue;
                    BigDecimal off = "PERCENT".equals(c.getKind())
                            ? l.getAmount().multiply(c.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : c.getValue();
                    l.setConcession(money2(l.getConcession().add(off).min(l.getAmount())));
                }
            }
            BigDecimal gross = billLines.stream().map(RebInvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal lineOff = billLines.stream().map(RebInvoiceLine::getConcession).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal concession = money2(lineOff.add(wholeBill).min(gross));
            BigDecimal net = gross.subtract(concession);
            if (net.signum() == 0) {
                out.add(new Planned(s, section, null, List.of(), "Nothing to pay after concessions"));
                continue;
            }
            RebInvoice inv = new RebInvoice();
            inv.setStudentId(s.getId());
            inv.setSessionId(session.getId());
            inv.setPeriod(period);
            inv.setDueDate(due);
            inv.setGross(gross);
            inv.setConcession(concession);
            inv.setNet(net);
            Optional<RebSponsorship> sp = sponsorshipService.covering(s.getId(), period);
            BigDecimal sponsorShare = sp.map(x -> x.getMonthlyAmount().min(net)).orElse(BigDecimal.ZERO);
            inv.setSponsorshipId(sp.map(RebSponsorship::getId).orElse(null));
            inv.setSponsorShare(money2(sponsorShare));
            inv.setFamilyShare(money2(net.subtract(sponsorShare)));
            out.add(new Planned(s, section, inv, billLines, null));
        }
        out.sort(Comparator.comparing((Planned p) -> p.section() == null ? "~" : p.section()).thenComparing(p -> p.student().getName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    static int nextNumber(List<String> used, String prefix) {
        int max = 0;
        for (String n : used) {
            try {
                max = Math.max(max, Integer.parseInt(n.substring(prefix.length())));
            } catch (RuntimeException ignored) {
                // typed by hand
            }
        }
        return max + 1;
    }

    // ---------------------------------------------------------------- bills

    /** Bills, filtered by month, section, status (OVERDUE = past its due date and not paid) and girl. */
    public List<Map<String, Object>> invoices(String period, Long sectionId, String status, String q) {
        String p = RebFeeService.period(period, "Month");
        List<RebInvoice> list = p != null ? invoices.findByPeriod(p) : invoices.findAll();
        Map<Long, RebStudent> girls = new HashMap<>();
        students.findAll().forEach(s -> girls.put(s.getId(), s));
        Map<Long, Map<Long, Long>> sectionOf = sectionsBySession();
        Map<Long, String> labels = studentService.sectionLabels();
        String query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebInvoice i : list) {
            RebStudent g = girls.get(i.getStudentId());
            Long sec = sectionOf.getOrDefault(i.getSessionId(), Map.of()).get(i.getStudentId());
            if (sectionId != null && !sectionId.equals(sec)) continue;
            boolean overdue = OPEN.contains(i.getStatus()) && i.getDueDate().isBefore(today);
            if (status != null && !status.isBlank() && !("OVERDUE".equalsIgnoreCase(status) ? overdue : status.equalsIgnoreCase(i.getStatus()))) continue;
            if (!query.isEmpty() && (g == null || !(g.getName().toLowerCase(Locale.ROOT).contains(query) || g.getAdmissionNo().toLowerCase(Locale.ROOT).contains(query)
                    || i.getInvoiceNo().toLowerCase(Locale.ROOT).contains(query)))) continue;
            Map<String, Object> r = Rows.of(i);
            r.put("student_name", g == null ? null : g.getName());
            r.put("admission_no", g == null ? null : g.getAdmissionNo());
            r.put("section", sec == null ? null : labels.get(sec));
            r.put("overdue", overdue);
            r.put("outstanding", i.outstanding("SPONSOR").add(i.outstanding("FAMILY")));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("period"))).reversed()
                .thenComparing(r -> String.valueOf(r.get("section")))
                .thenComparing(r -> String.valueOf(r.get("student_name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** session id -> (girl -> section) */
    private Map<Long, Map<Long, Long>> sectionsBySession() {
        Map<Long, Map<Long, Long>> out = new HashMap<>();
        enrollments.findAll().forEach(e -> out.computeIfAbsent(e.getSessionId(), k -> new HashMap<>()).put(e.getStudentId(), e.getSectionId()));
        return out;
    }

    /** A bill with its fee heads and the payments that paid it. */
    public Map<String, Object> invoice(Long id) {
        RebInvoice i = invoices.findById(id).orElseThrow(() -> notFound("Bill"));
        Map<Long, String> headNames = new HashMap<>();
        heads.findAll().forEach(h -> headNames.put(h.getId(), h.getName()));
        List<Map<String, Object>> lineRows = new ArrayList<>();
        for (RebInvoiceLine l : lines.findByInvoiceId(id)) {
            Map<String, Object> r = Rows.of(l);
            r.put("fee_head_name", headNames.get(l.getFeeHeadId()));
            lineRows.add(r);
        }
        List<Map<String, Object>> paid = new ArrayList<>();
        for (RebPaymentAllocation a : allocations.findByInvoiceId(id)) {
            RebPayment p = payments.findById(a.getPaymentId()).orElse(null);
            if (p == null || !"VALID".equals(p.getStatus())) continue;
            paid.add(Map.of("payment_id", p.getId(), "receipt_no", p.getReceiptNo(), "paid_on", p.getPaidOn(), "share", a.getShare(), "amount", a.getAmount()));
        }
        Map<String, Object> out = Rows.of(i);
        RebStudent g = students.findById(i.getStudentId()).orElse(null);
        out.put("student_name", g == null ? null : g.getName());
        out.put("admission_no", g == null ? null : g.getAdmissionNo());
        out.put("label", label(i.getPeriod()));
        out.put("lines", lineRows);
        out.put("payments", paid);
        return out;
    }

    /** Body: { reason }. The rest of the bill is forgiven (what was paid stays). Needs "fee concessions & cancellations". */
    @Transactional
    public void waive(Long id, Map<String, Object> body) {
        RebInvoice i = requireOpen(id);
        i.setStatus("WAIVED");
        i.setNote(text(body.get("reason"), "Reason", 500, true));
        invoices.save(i);
    }

    /** Body: { reason }. Only a bill nothing was paid on (e.g. made by mistake); a new one can then be made. */
    @Transactional
    public void cancel(Long id, Map<String, Object> body) {
        RebInvoice i = requireOpen(id);
        if (i.getSponsorPaid().signum() > 0 || i.getFamilyPaid().signum() > 0) {
            throw bad("Money was already paid on this bill: cancel those receipts first, or waive the rest.");
        }
        i.setStatus("CANCELLED"); // a corrected bill for the month can then be made
        i.setNote(text(body.get("reason"), "Reason", 500, true));
        invoices.save(i);
    }

    private RebInvoice requireOpen(Long id) {
        RebInvoice i = invoices.findById(id).orElseThrow(() -> notFound("Bill"));
        if (!OPEN.contains(i.getStatus())) throw bad("This bill is already " + i.getStatus().toLowerCase(Locale.ROOT) + ".");
        return i;
    }

    // ---------------------------------------------------------------- payments

    /** What a girl still owes, per bill and share, oldest first (for the Collect screen). */
    public Map<String, Object> due(Long studentId) {
        RebStudent g = studentService.require(studentId);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal family = BigDecimal.ZERO, sponsor = BigDecimal.ZERO;
        for (RebInvoice i : invoices.findByStudentIdOrderByPeriodAsc(studentId)) {
            BigDecimal f = i.outstanding("FAMILY"), s = i.outstanding("SPONSOR");
            if (f.signum() == 0 && s.signum() == 0) continue;
            family = family.add(f);
            sponsor = sponsor.add(s);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", i.getId());
            r.put("invoice_no", i.getInvoiceNo());
            r.put("period", i.getPeriod());
            r.put("label", label(i.getPeriod()));
            r.put("due_date", i.getDueDate());
            r.put("overdue", i.getDueDate().isBefore(LocalDate.now()));
            r.put("family_outstanding", f);
            r.put("sponsor_outstanding", s);
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", Rows.pick(g, "id", "admission_no", "name", "fee_plan", "residential", "status"));
        out.put("bills", rows);
        out.put("family_total", family);
        out.put("sponsor_total", sponsor);
        out.put("sponsorship", sponsorshipService.covering(studentId, YearMonth.now().toString()).map(s -> {
            User u = users.findById(s.getSponsorUserId()).orElse(null);
            return Map.<String, Object>of("id", s.getId(), "sponsor_user_id", s.getSponsorUserId(), "monthly_amount", s.getMonthlyAmount(),
                    "sponsor_name", u == null ? "" : u.getName(), "sponsor_code", u == null || u.getUserId() == null ? "" : u.getUserId());
        }).orElse(null));
        return out;
    }

    /**
     * Records a payment. Body: { studentId, payer FAMILY | SPONSOR, sponsorUserId (sponsor payments; default: her
     * current sponsor), amount, paidOn (default today), method, reference, note, invoiceIds (optional: which bills,
     * else the oldest unpaid ones) }. The amount can't be more than what is due on those bills.
     */
    @Transactional
    public RebPayment collect(Map<String, Object> body) {
        RebStudent g = studentService.require(id(body.get("studentId")));
        String payer = code(body.get("payer"), Set.of("FAMILY", "SPONSOR"), "FAMILY", "payer");
        BigDecimal amount = money(body.get("amount"), "Amount");
        if (amount == null || amount.signum() <= 0) throw bad("Enter the amount received.");
        amount = money2(amount);
        LocalDate paidOn = date(body.get("paidOn"), "Date");
        if (paidOn == null) paidOn = LocalDate.now();
        if (paidOn.isAfter(LocalDate.now())) throw bad("The payment date is in the future.");
        String method = code(body.get("method"), METHODS, "CASH", "payment method");
        String reference = text(body.get("reference"), "Reference", 100, false);
        if (!"CASH".equals(method) && reference == null) throw bad("Enter the " + ("CHEQUE".equals(method) ? "cheque number" : "transaction reference") + ".");

        Long sponsorId = null;
        if ("SPONSOR".equals(payer)) {
            sponsorId = id(body.get("sponsorUserId"));
            if (sponsorId == null) sponsorId = sponsorshipService.covering(g.getId(), YearMonth.now().toString()).map(RebSponsorship::getSponsorUserId).orElse(null);
            if (sponsorId == null || users.findById(sponsorId).filter(u -> UserRoles.hasAny(u, ServiceSupport.SPONSOR_ROLES, users)).isEmpty()) {
                throw bad("Choose the sponsor who paid.");
            }
        }

        // the bills to pay, oldest first
        List<RebInvoice> open = new ArrayList<>(invoices.findByStudentIdOrderByPeriodAsc(g.getId()));
        open.removeIf(i -> i.outstanding(payer).signum() == 0);
        if (body.get("invoiceIds") instanceof List<?> chosen && !chosen.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            chosen.forEach(x -> ids.add(id(x)));
            open.removeIf(i -> !ids.contains(i.getId()));
        }
        BigDecimal due = open.stream().map(i -> i.outstanding(payer)).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (due.signum() == 0) throw bad("Nothing is due on the " + ("SPONSOR".equals(payer) ? "sponsor's" : "family's") + " share. Make the month's bills first.");
        if (amount.compareTo(due) > 0) throw bad("That is more than is due (₹" + due.toPlainString() + "). Make the coming months' bills first to take money in advance.");

        RebSession session = open.isEmpty() ? setup.currentSession() : sessions.findById(open.get(0).getSessionId()).orElse(setup.currentSession());
        String prefix = setup.setting("receipt_prefix", "REB-R") + "/" + session.getName() + "/";
        RebPayment p = new RebPayment();
        p.setReceiptNo(prefix + String.format("%04d", nextNumber(payments.findNumbersStartingWith(prefix), prefix)));
        p.setStudentId(g.getId());
        p.setPayer(payer);
        p.setSponsorUserId(sponsorId);
        p.setAmount(amount);
        p.setPaidOn(paidOn);
        p.setMethod(method);
        p.setReference(reference);
        p.setNote(text(body.get("note"), "Note", 500, false));
        payments.save(p);

        BigDecimal left = amount;
        for (RebInvoice i : open) {
            if (left.signum() == 0) break;
            BigDecimal part = i.outstanding(payer).min(left);
            RebPaymentAllocation a = new RebPaymentAllocation();
            a.setPaymentId(p.getId());
            a.setInvoiceId(i.getId());
            a.setShare(payer);
            a.setAmount(part);
            allocations.save(a);
            if ("SPONSOR".equals(payer)) i.setSponsorPaid(i.getSponsorPaid().add(part));
            else i.setFamilyPaid(i.getFamilyPaid().add(part));
            refreshStatus(i);
            invoices.save(i);
            left = left.subtract(part);
        }
        return p;
    }

    static void refreshStatus(RebInvoice i) {
        if ("WAIVED".equals(i.getStatus()) || "CANCELLED".equals(i.getStatus())) return;
        BigDecimal paid = i.getSponsorPaid().add(i.getFamilyPaid());
        i.setStatus(paid.compareTo(i.getNet()) >= 0 ? "PAID" : paid.signum() > 0 ? "PARTIAL" : "DUE");
    }

    /** Body: { reason }. The receipt stays (marked cancelled); the bills it paid are unpaid again. */
    @Transactional
    public void cancelPayment(Long id, Map<String, Object> body) {
        RebPayment p = payments.findById(id).orElseThrow(() -> notFound("Payment"));
        if (!"VALID".equals(p.getStatus())) throw bad("This receipt is already cancelled.");
        p.setCancelReason(text(body.get("reason"), "Reason", 500, true));
        p.setStatus("CANCELLED");
        payments.save(p);
        for (RebPaymentAllocation a : allocations.findByPaymentId(id)) {
            RebInvoice i = invoices.findById(a.getInvoiceId()).orElse(null);
            if (i == null) continue;
            if ("SPONSOR".equals(a.getShare())) i.setSponsorPaid(i.getSponsorPaid().subtract(a.getAmount()).max(BigDecimal.ZERO));
            else i.setFamilyPaid(i.getFamilyPaid().subtract(a.getAmount()).max(BigDecimal.ZERO));
            if ("PAID".equals(i.getStatus()) || "PARTIAL".equals(i.getStatus())) i.setStatus("DUE");
            refreshStatus(i);
            invoices.save(i);
        }
    }

    /** The day book: payments between two days (default: today), with totals by method. */
    public Map<String, Object> payments(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now() : from, end = to == null ? start : to;
        if (end.isBefore(start)) throw bad("The end date is before the start date.");
        Map<Long, RebStudent> girls = new HashMap<>();
        students.findAll().forEach(s -> girls.put(s.getId(), s));
        List<RebPayment> list = payments.findByPaidOnBetweenOrderByPaidOnAscIdAsc(start, end);
        Map<Long, String> who = new HashMap<>();
        users.findAllById(list.stream().flatMap(p -> java.util.stream.Stream.of(p.getCreatedBy(), p.getSponsorUserId())).filter(Objects::nonNull).distinct().toList())
                .forEach(u -> who.put(u.getId(), u.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> byMethod = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RebPayment p : list) {
            Map<String, Object> r = Rows.of(p);
            RebStudent g = girls.get(p.getStudentId());
            r.put("student_name", g == null ? null : g.getName());
            r.put("admission_no", g == null ? null : g.getAdmissionNo());
            r.put("sponsor_name", who.get(p.getSponsorUserId()));
            r.put("received_by", who.get(p.getCreatedBy()));
            rows.add(r);
            if ("VALID".equals(p.getStatus())) {
                total = total.add(p.getAmount());
                byMethod.merge(p.getMethod(), p.getAmount(), BigDecimal::add);
            }
        }
        return Map.of("from", start, "to", end, "payments", rows, "total", total, "by_method", byMethod);
    }

    // ---------------------------------------------------------------- ledger and dues

    /** A girl's bills and payments in date order with the running balance (what she owes after each line). */
    public Map<String, Object> ledger(Long studentId) {
        RebStudent g = studentService.require(studentId);
        record Entry(LocalDate day, int order, Map<String, Object> row, BigDecimal change) {}
        List<Entry> entries = new ArrayList<>();
        for (RebInvoice i : invoices.findByStudentIdOrderByPeriodAsc(studentId)) {
            if ("CANCELLED".equals(i.getStatus())) continue;
            BigDecimal owed = "WAIVED".equals(i.getStatus()) ? i.getSponsorPaid().add(i.getFamilyPaid()) : i.getNet();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "BILL");
            r.put("id", i.getId());
            r.put("date", YearMonth.parse(i.getPeriod()).atDay(1));
            r.put("text", "Bill " + i.getInvoiceNo() + " for " + label(i.getPeriod()) + ("WAIVED".equals(i.getStatus()) ? " (rest waived)" : ""));
            r.put("status", i.getStatus());
            r.put("debit", owed);
            r.put("sponsor_share", i.getSponsorShare());
            entries.add(new Entry(YearMonth.parse(i.getPeriod()).atDay(1), 0, r, owed));
        }
        for (RebPayment p : payments.findByStudentIdOrderByPaidOnAscIdAsc(studentId)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "PAYMENT");
            r.put("id", p.getId());
            r.put("date", p.getPaidOn());
            r.put("text", "Receipt " + p.getReceiptNo() + " (" + ("SPONSOR".equals(p.getPayer()) ? "sponsor" : "family") + ", "
                    + p.getMethod().toLowerCase(Locale.ROOT) + ")" + ("CANCELLED".equals(p.getStatus()) ? " - cancelled" : ""));
            r.put("status", p.getStatus());
            r.put("credit", p.getAmount());
            entries.add(new Entry(p.getPaidOn(), 1, r, "VALID".equals(p.getStatus()) ? p.getAmount().negate() : BigDecimal.ZERO));
        }
        entries.sort(Comparator.comparing(Entry::day).thenComparingInt(Entry::order));
        BigDecimal balance = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Entry e : entries) {
            balance = balance.add(e.change());
            e.row().put("balance", balance);
            rows.add(e.row());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", Rows.pick(g, "id", "admission_no", "name", "fee_plan"));
        out.put("rows", rows);
        out.put("balance", balance);
        return out;
    }

    /**
     * Girls who owe money: family and sponsor shares, how many bills are unpaid and the oldest unpaid month. Only
     * overdue bills count (due date passed) unless all is set.
     */
    public List<Map<String, Object>> dues(Long sectionId, boolean all) {
        LocalDate today = LocalDate.now();
        Map<Long, RebStudent> girls = new HashMap<>();
        students.findAll().forEach(s -> girls.put(s.getId(), s));
        Map<Long, Map<Long, Long>> sectionOf = sectionsBySession();
        Map<Long, String> labels = studentService.sectionLabels();
        Long current = setup.sessionsCurrentId();
        Map<Long, Map<String, Object>> byGirl = new LinkedHashMap<>();
        for (RebInvoice i : invoices.findByStatusIn(OPEN)) {
            if (!all && !i.getDueDate().isBefore(today)) continue;
            BigDecimal f = i.outstanding("FAMILY"), s = i.outstanding("SPONSOR");
            if (f.signum() == 0 && s.signum() == 0) continue;
            Long sec = current == null ? null : sectionOf.getOrDefault(current, Map.of()).get(i.getStudentId());
            if (sectionId != null && !sectionId.equals(sec)) continue;
            Map<String, Object> r = byGirl.computeIfAbsent(i.getStudentId(), k -> {
                RebStudent g = girls.get(k);
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("student_id", k);
                n.put("name", g == null ? null : g.getName());
                n.put("admission_no", g == null ? null : g.getAdmissionNo());
                n.put("section", sec == null ? null : labels.get(sec));
                n.put("status", g == null ? null : g.getStatus());
                n.put("bills", 0);
                n.put("oldest", i.getPeriod());
                n.put("family_outstanding", BigDecimal.ZERO);
                n.put("sponsor_outstanding", BigDecimal.ZERO);
                return n;
            });
            r.put("bills", (Integer) r.get("bills") + 1);
            if (i.getPeriod().compareTo((String) r.get("oldest")) < 0) r.put("oldest", i.getPeriod());
            r.put("family_outstanding", ((BigDecimal) r.get("family_outstanding")).add(f));
            r.put("sponsor_outstanding", ((BigDecimal) r.get("sponsor_outstanding")).add(s));
        }
        List<Map<String, Object>> rows = new ArrayList<>(byGirl.values());
        rows.forEach(r -> {
            r.put("oldest_label", label((String) r.get("oldest")));
            r.put("total", ((BigDecimal) r.get("family_outstanding")).add((BigDecimal) r.get("sponsor_outstanding")));
        });
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("total")).reversed());
        return rows;
    }

    /** This month at a glance, for the REB dashboard (only for people who may see fees). */
    public Map<String, Object> monthSummary() {
        if (!Access.can(Section.REB_FEES, Section.Level.VIEW)) return null;
        String period = YearMonth.now().toString();
        BigDecimal billed = BigDecimal.ZERO;
        for (RebInvoice i : invoices.findByPeriod(period)) if (!"CANCELLED".equals(i.getStatus())) billed = billed.add(i.getNet());
        LocalDate first = YearMonth.now().atDay(1);
        BigDecimal collected = BigDecimal.ZERO;
        for (RebPayment p : payments.findByPaidOnBetweenOrderByPaidOnAscIdAsc(first, LocalDate.now())) {
            if ("VALID".equals(p.getStatus())) collected = collected.add(p.getAmount());
        }
        BigDecimal overdue = BigDecimal.ZERO;
        int girls = 0;
        for (Map<String, Object> d : dues(null, false)) {
            overdue = overdue.add((BigDecimal) d.get("total"));
            girls++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("label", label(period));
        out.put("billed", billed);
        out.put("bills_made", invoices.findByPeriod(period).stream().anyMatch(i -> !"CANCELLED".equals(i.getStatus())));
        out.put("collected", collected);
        out.put("overdue", overdue);
        out.put("girls_overdue", girls);
        return out;
    }
}
