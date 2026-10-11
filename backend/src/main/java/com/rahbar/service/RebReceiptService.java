package com.rahbar.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.dto.RebPdf;
import com.rahbar.entity.*;
import com.rahbar.repository.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static com.rahbar.util.RebSupport.notFound;

/** The printable receipt of an REB payment (marked CANCELLED when it was cancelled). */
@Service
public class RebReceiptService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final Color GREEN = new Color(0x0E, 0x6B, 0x47);

    private final RebPaymentRepository payments;
    private final RebPaymentAllocationRepository allocations;
    private final RebInvoiceRepository invoices;
    private final RebStudentRepository students;
    private final RebStudentService studentService;
    private final RebEnrollmentRepository enrollments;
    private final RebSetupService setup;
    private final UserRepository users;

    public RebReceiptService(RebPaymentRepository payments, RebPaymentAllocationRepository allocations, RebInvoiceRepository invoices,
                             RebStudentRepository students, RebStudentService studentService, RebEnrollmentRepository enrollments,
                             RebSetupService setup, UserRepository users) {
        this.payments = payments;
        this.allocations = allocations;
        this.invoices = invoices;
        this.students = students;
        this.studentService = studentService;
        this.enrollments = enrollments;
        this.setup = setup;
        this.users = users;
    }

    public RebPdf receipt(Long paymentId) {
        RebPayment p = payments.findById(paymentId).orElseThrow(() -> notFound("Payment"));
        RebStudent g = students.findById(p.getStudentId()).orElseThrow(() -> notFound("Student"));
        Map<String, String> settings = setup.settingValues();
        String school = blankTo(settings.get("school_name"), "Rahbar-e-Banaat School-cum-Madarsa");
        Optional<RebEnrollment> enrolment = enrollments.findByStudentId(g.getId()).stream().max(Comparator.comparing(RebEnrollment::getId));
        String section = enrolment.map(e -> studentService.sectionLabels().get(e.getSectionId())).orElse(null);
        String payer = "SPONSOR".equals(p.getPayer())
                ? "Sponsor: " + users.findById(p.getSponsorUserId()).map(u -> u.getName() + (u.getUserId() == null ? "" : " (" + u.getUserId() + ")")).orElse("")
                : "Family";
        String receivedBy = p.getCreatedBy() == null ? "" : users.findById(p.getCreatedBy()).map(User::getName).orElse("");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A5.rotate(), 36, 36, 30, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, GREEN);
            Font sub = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font text = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font big = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font red = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.RED);

            Paragraph para = new Paragraph(school, brand);
            para.setAlignment(Element.ALIGN_CENTER);
            doc.add(para);
            String address = String.join(" · ", java.util.stream.Stream.of(settings.get("school_address"), settings.get("school_phone"))
                    .filter(v -> v != null && !v.isBlank()).toList());
            if (!address.isEmpty()) {
                para = new Paragraph(address, sub);
                para.setAlignment(Element.ALIGN_CENTER);
                doc.add(para);
            }
            para = new Paragraph("FEE RECEIPT", title);
            para.setAlignment(Element.ALIGN_CENTER);
            para.setSpacingBefore(6);
            para.setSpacingAfter(8);
            doc.add(para);
            if ("CANCELLED".equals(p.getStatus())) {
                para = new Paragraph("CANCELLED" + (p.getCancelReason() == null ? "" : ": " + p.getCancelReason()), red);
                para.setAlignment(Element.ALIGN_CENTER);
                para.setSpacingAfter(6);
                doc.add(para);
            }

            PdfPTable t = new PdfPTable(new float[]{1.6f, 3f, 1.4f, 2.2f});
            t.setWidthPercentage(100);
            cell(t, "Receipt no.", bold); cell(t, p.getReceiptNo(), text); cell(t, "Date", bold); cell(t, p.getPaidOn().format(DAY), text);
            cell(t, "Student", bold); cell(t, g.getName() + " (" + g.getAdmissionNo() + ")", text); cell(t, "Class", bold); cell(t, section, text);
            cell(t, "Paid by", bold); cell(t, payer, text); cell(t, "Mode", bold);
            cell(t, p.getMethod().charAt(0) + p.getMethod().substring(1).toLowerCase(Locale.ROOT) + (p.getReference() == null ? "" : " · " + p.getReference()), text);
            doc.add(t);

            PdfPTable bills = new PdfPTable(new float[]{4f, 2f});
            bills.setWidthPercentage(100);
            bills.setSpacingBefore(8);
            cell(bills, "Towards", bold);
            cell(bills, "Amount (Rs.)", bold);
            for (RebPaymentAllocation a : allocations.findByPaymentId(p.getId())) {
                RebInvoice i = invoices.findById(a.getInvoiceId()).orElse(null);
                cell(bills, i == null ? "" : "Fees for " + RebBillingService.label(i.getPeriod()) + " (bill " + i.getInvoiceNo() + ")", text);
                cell(bills, a.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(), text);
            }
            cell(bills, "Total", bold);
            cell(bills, p.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(), big);
            doc.add(bills);

            para = new Paragraph("Rupees " + words(p.getAmount()) + " only.", text);
            para.setSpacingBefore(6);
            doc.add(para);
            if (p.getNote() != null) doc.add(new Paragraph(p.getNote(), sub));
            para = new Paragraph("\nReceived by: " + receivedBy, text);
            para.setAlignment(Element.ALIGN_RIGHT);
            doc.add(para);
            para = new Paragraph("Generated by Rahbar.", sub);
            para.setSpacingBefore(10);
            doc.add(para);
            doc.close();
            return new RebPdf(out.toByteArray(), "Receipt_" + p.getReceiptNo().replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf");
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not create the receipt", e);
        }
    }

    private static void cell(PdfPTable t, String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s == null || s.isBlank() ? "-" : s, f));
        c.setPadding(5);
        c.setBorderColor(new Color(0xD6, 0xE0, 0xDA));
        t.addCell(c);
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.strip();
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
            "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** 12500.50 -> "Twelve Thousand Five Hundred and Fifty Paise" (Indian system: thousand, lakh, crore). */
    static String words(BigDecimal amount) {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = a.longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String r = rupees == 0 ? "Zero" : indian(rupees);
        return paise == 0 ? r : r + " and " + below100(paise) + " Paise";
    }

    private static String indian(long n) {
        List<String> parts = new ArrayList<>();
        long crore = n / 10_000_000; n %= 10_000_000;
        long lakh = n / 100_000; n %= 100_000;
        long thousand = n / 1000; n %= 1000;
        long hundred = n / 100; n %= 100;
        if (crore > 0) parts.add(indian(crore) + " Crore");
        if (lakh > 0) parts.add(below100((int) lakh) + " Lakh");
        if (thousand > 0) parts.add(below100((int) thousand) + " Thousand");
        if (hundred > 0) parts.add(ONES[(int) hundred] + " Hundred");
        if (n > 0) parts.add((parts.isEmpty() ? "" : "and ") + below100((int) n));
        return String.join(" ", parts);
    }

    private static String below100(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }
}
