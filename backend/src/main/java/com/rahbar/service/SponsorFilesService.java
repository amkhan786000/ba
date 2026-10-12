package com.rahbar.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.dto.ReportFile;
import com.rahbar.entity.Payment;
import com.rahbar.entity.PaymentInstallment;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.PaymentInstallmentRepository;
import com.rahbar.repository.PaymentRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Files a sponsor can download from Payments: a receipt for each payment they made (PDF), their payment history
 * (Excel, one year or all), and their upcoming installments as calendar reminders (.ics, with a reminder 3 days
 * before each due date).
 */
@Service
public class SponsorFilesService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final PaymentRepository payments;
    private final PaymentInstallmentRepository installments;
    private final UserRepository users;
    private final GrantorGranteeRepository mappings;
    private final ReportService reports;

    public SponsorFilesService(PaymentRepository payments, PaymentInstallmentRepository installments, UserRepository users,
                               GrantorGranteeRepository mappings, ReportService reports) {
        this.payments = payments;
        this.installments = installments;
        this.users = users;
        this.mappings = mappings;
        this.reports = reports;
    }

    /** Receipt number shown on the receipt, e.g. RB-PAY-000123. */
    static String receiptNo(Long paymentId) {
        return String.format("RB-PAY-%06d", paymentId);
    }

    // ---------------------------------------------------------------- receipt

    /** The receipt for one of the sponsor's own paid payments (404 for anyone else's). */
    @Transactional(readOnly = true)
    public byte[] receipt(Long sponsorId, Long paymentId) {
        Payment p = payments.findById(paymentId)
                .filter(x -> sponsorId.equals(x.getGrantorId()) && "Paid".equalsIgnoreCase(x.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found."));
        User sponsor = users.findById(sponsorId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found."));
        User student = users.findById(p.getGranteeId()).orElse(null);
        Integer installmentNo = installments.findByPaymentId(paymentId).map(PaymentInstallment::getInstallmentNo).orElse(null);
        try {
            return renderReceipt(p, sponsor, student, installmentNo);
        } catch (DocumentException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the receipt.");
        }
    }

    private static byte[] renderReceipt(Payment p, User sponsor, User student, Integer installmentNo) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5.rotate(), 36, 36, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x3D, 0x6B, 0x1F));
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font text = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        doc.add(new Paragraph("Rahbar - Bihar Anjuman", brand));
        doc.add(new Paragraph("Payment receipt", title));
        doc.add(new Paragraph(" "));
        PdfPTable t = new PdfPTable(new float[]{1.3f, 2.7f});
        t.setWidthPercentage(100);
        row(t, "Receipt no.", receiptNo(p.getPaymentId()), bold, text);
        row(t, "Received from", sponsor.getName() + " (" + sponsor.getUserId() + ")", bold, text);
        row(t, "Amount", "INR " + MONEY.format(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount()), bold, bold);
        row(t, "Payment date", p.getPaymentDate() == null ? "-" : p.getPaymentDate().toLocalDate().format(DAY), bold, text);
        row(t, "For the education of", student == null ? "-" : student.getName() + " (" + student.getUserId() + ")", bold, text);
        if (installmentNo != null) row(t, "Installment", "#" + installmentNo, bold, text);
        doc.add(t);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Thank you for supporting a student's education through Rahbar.", text));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("This receipt acknowledges your payment as recorded in Rahbar. Generated on "
                + LocalDate.now().format(DAY) + ".", small));
        doc.close();
        return out.toByteArray();
    }

    private static void row(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell a = new PdfPCell(new Phrase(label, lf));
        PdfPCell b = new PdfPCell(new Phrase(value, vf));
        for (PdfPCell c : List.of(a, b)) {
            c.setPadding(5);
            c.setBorderColor(new Color(0xD6, 0xE0, 0xDA));
            t.addCell(c);
        }
    }

    // ---------------------------------------------------------------- Excel history

    /** The sponsor's paid payments (one calendar year, or all when year is null), oldest first, as an Excel file. */
    @Transactional(readOnly = true)
    public ReportFile history(Long sponsorId, Integer year) {
        Map<Long, User> students = new HashMap<>();
        List<Payment> list = payments.findByGrantorIdOrderByPaymentDateDesc(sponsorId).stream()
                .filter(p -> "Paid".equalsIgnoreCase(p.getStatus()) && p.getPaymentDate() != null)
                .filter(p -> year == null || p.getPaymentDate().getYear() == year)
                .sorted(Comparator.comparing(Payment::getPaymentDate)).toList();
        users.findAllById(list.stream().map(Payment::getGranteeId).distinct().toList()).forEach(u -> students.put(u.getId(), u));
        Map<Long, Integer> installmentNo = new HashMap<>();
        for (PaymentInstallment i : installments.findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(sponsorId)) {
            if (i.getPaymentId() != null) installmentNo.put(i.getPaymentId(), i.getInstallmentNo());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment p : list) {
            User s = students.get(p.getGranteeId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("receipt_no", receiptNo(p.getPaymentId()));
            r.put("payment_date", p.getPaymentDate().toLocalDate());
            r.put("student_id", s == null ? null : s.getUserId());
            r.put("student_name", s == null ? null : s.getName());
            r.put("installment", installmentNo.get(p.getPaymentId()));
            r.put("amount", p.getAmount());
            r.put("spent_proof", p.getStudentProofUrl() != null && !p.getStudentProofUrl().isBlank() ? "Received" : "Pending");
            rows.add(r);
        }
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, year == null ? "You have no payments yet." : "You have no payments in " + year + ".");
        return reports.build(rows, "Rahbar_payments_" + (year == null ? "all" : year), "excel", "payment history");
    }

    // ---------------------------------------------------------------- calendar

    /** Unpaid installments due from today, for the sponsor's current students, as an iCalendar file. */
    @Transactional(readOnly = true)
    public byte[] calendar(Long sponsorId) {
        Set<Long> current = new HashSet<>();
        mappings.findByGrantorId(sponsorId).forEach(m -> current.add(m.getGranteeId()));
        LocalDate today = LocalDate.now();
        List<PaymentInstallment> upcoming = installments.findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(sponsorId).stream()
                .filter(i -> i.getPaymentId() == null && i.getDueDate() != null && !i.getDueDate().isBefore(today) && current.contains(i.getGranteeId()))
                .toList();
        Map<Long, String> names = new HashMap<>();
        users.findAllById(current).forEach(u -> names.put(u.getId(), u.getName()));
        return ics(upcoming, names, LocalDateTime.now()).getBytes(StandardCharsets.UTF_8);
    }

    /** The iCalendar text: one all-day event per installment with a reminder 3 days before. */
    static String ics(List<PaymentInstallment> upcoming, Map<Long, String> names, LocalDateTime now) {
        DateTimeFormatter date = DateTimeFormatter.BASIC_ISO_DATE;
        String stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
        StringBuilder b = new StringBuilder();
        line(b, "BEGIN:VCALENDAR");
        line(b, "VERSION:2.0");
        line(b, "PRODID:-//Rahbar Bihar Anjuman//Sponsor payments//EN");
        line(b, "CALSCALE:GREGORIAN");
        line(b, "X-WR-CALNAME:Rahbar sponsorship payments");
        for (PaymentInstallment i : upcoming) {
            String student = names.getOrDefault(i.getGranteeId(), "your student");
            String amount = "INR " + MONEY.format(i.getAmount() == null ? BigDecimal.ZERO : i.getAmount());
            line(b, "BEGIN:VEVENT");
            line(b, "UID:rahbar-installment-" + i.getInstallmentId() + "@rahbar");
            line(b, "DTSTAMP:" + stamp);
            line(b, "DTSTART;VALUE=DATE:" + i.getDueDate().format(date));
            line(b, "DTEND;VALUE=DATE:" + i.getDueDate().plusDays(1).format(date));
            line(b, "SUMMARY:" + escape("Rahbar: installment #" + i.getInstallmentNo() + " for " + student + " (" + amount + ")"));
            line(b, "DESCRIPTION:" + escape("Installment #" + i.getInstallmentNo() + " of " + amount + " for " + student
                    + " is due. Pay and upload the receipt on your Rahbar Payments page."));
            line(b, "BEGIN:VALARM");
            line(b, "TRIGGER:-P3D");
            line(b, "ACTION:DISPLAY");
            line(b, "DESCRIPTION:" + escape("Rahbar installment due in 3 days"));
            line(b, "END:VALARM");
            line(b, "END:VEVENT");
        }
        line(b, "END:VCALENDAR");
        return b.toString();
    }

    private static void line(StringBuilder b, String s) {
        b.append(s).append("\r\n");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
