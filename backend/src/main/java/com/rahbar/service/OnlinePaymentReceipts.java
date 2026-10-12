package com.rahbar.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.entity.OnlinePayment;
import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The 80G receipt of an online payment (sponsor installments or a donation), as a PDF. */
@Service
public class OnlinePaymentReceipts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final OnlinePaymentSettings settings;

    public OnlinePaymentReceipts(OnlinePaymentSettings settings) {
        this.settings = settings;
    }

    /** Receipt number by financial year (April-March), e.g. RB/80G/2026-27/000123. */
    static String receiptNo(Long id, LocalDateTime paidAt) {
        int y = paidAt.getMonthValue() >= 4 ? paidAt.getYear() : paidAt.getYear() - 1;
        return String.format("RB/80G/%d-%02d/%06d", y, (y + 1) % 100, id);
    }

    /** lines: what it was for, e.g. "Installment #3 for Asha (ST11)". */
    public byte[] pdf(OnlinePayment p, List<String> lines) {
        if (!OnlinePayment.PAID.equals(p.getStatus())) throw new ApiException(HttpStatus.NOT_FOUND, "Receipt not found.");
        Map<String, String> org = settings.receipt();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(0x3D, 0x6B, 0x1F));
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font text = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);

            doc.add(new Paragraph(blankTo(org.get("receipt.legal_name"), "Bihar Anjuman"), brand));
            if (!org.get("receipt.address").isBlank()) doc.add(new Paragraph(org.get("receipt.address"), small));
            String ids = join("PAN " + org.get("receipt.pan"), org.get("receipt.12a_number").isBlank() ? "" : "12A " + org.get("receipt.12a_number"),
                    "80G " + org.get("receipt.80g_number") + (org.get("receipt.80g_valid").isBlank() ? "" : " (" + org.get("receipt.80g_valid") + ")"));
            doc.add(new Paragraph(ids, small));
            Paragraph t = new Paragraph("Receipt for donation (Section 80G of the Income Tax Act, 1961)", title);
            t.setSpacingBefore(12);
            t.setSpacingAfter(8);
            doc.add(t);

            PdfPTable tbl = new PdfPTable(new float[]{1.4f, 3f});
            tbl.setWidthPercentage(100);
            row(tbl, "Receipt no.", p.getReceiptNo(), bold, text);
            row(tbl, "Date", p.getPaidAt().format(DAY), bold, text);
            row(tbl, "Received from", p.getDonorName(), bold, text);
            if (p.getDonorPan() != null && !p.getDonorPan().isBlank()) row(tbl, "Donor's PAN", p.getDonorPan(), bold, text);
            if (p.getDonorAddress() != null && !p.getDonorAddress().isBlank()) row(tbl, "Donor's address", p.getDonorAddress(), bold, text);
            row(tbl, "Amount", "INR " + MONEY.format(p.getAmount()) + " (" + Words.inr(p.getAmount()) + ")", bold, bold);
            row(tbl, "Mode", "Online (Razorpay)" + (p.getGatewayPaymentId() == null ? "" : ", payment ID " + p.getGatewayPaymentId()), bold, text);
            String purpose = OnlinePayment.SPONSOR.equals(p.getPurpose()) ? "Scholarship for students' education" : blankTo(p.getNote(), "General donation");
            row(tbl, "Towards", purpose, bold, text);
            doc.add(tbl);
            if (!lines.isEmpty()) {
                Paragraph d = new Paragraph("Details: " + String.join("; ", lines) + ".", small);
                d.setSpacingBefore(6);
                doc.add(d);
            }
            Paragraph thanks = new Paragraph("Thank you for supporting education through Bihar Anjuman.", text);
            thanks.setSpacingBefore(14);
            doc.add(thanks);
            if (!org.get("receipt.signatory").isBlank()) {
                Paragraph s = new Paragraph("For " + blankTo(org.get("receipt.legal_name"), "Bihar Anjuman") + "\n" + org.get("receipt.signatory"), text);
                s.setSpacingBefore(18);
                doc.add(s);
            }
            Paragraph foot = new Paragraph("This is a computer-generated receipt and needs no signature. Donations are eligible for deduction "
                    + "under Section 80G as per the registration above; the donor's PAN is reported to the Income Tax Department (Form 10BD).", small);
            foot.setSpacingBefore(18);
            doc.add(foot);
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the receipt.");
        }
    }

    private static void row(PdfPTable t, String label, String value, Font lf, Font vf) {
        for (PdfPCell c : List.of(new PdfPCell(new Phrase(label, lf)), new PdfPCell(new Phrase(value == null ? "-" : value, vf)))) {
            c.setPadding(5);
            c.setBorderColor(new Color(0xD6, 0xE0, 0xDA));
            t.addCell(c);
        }
    }

    private static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) if (p != null && !p.isBlank() && !p.endsWith(" ")) b.append(b.length() == 0 ? "" : " | ").append(p);
        return b.toString();
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Rupee amounts in words, Indian style (lakh, crore), e.g. "Rupees Fourteen Thousand Only". */
    static final class Words {
        private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven",
                "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        static String inr(BigDecimal amount) {
            long rupees = amount.longValue();
            int paise = amount.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).intValue();
            String w = rupees == 0 ? "Zero" : words(rupees).trim();
            return "Rupees " + w + (paise > 0 ? " and " + words(paise).trim() + " Paise" : "") + " Only";
        }

        private static String words(long n) {
            if (n >= 10_000_000) return words(n / 10_000_000) + " Crore " + words(n % 10_000_000);
            if (n >= 100_000) return words(n / 100_000) + " Lakh " + words(n % 100_000);
            if (n >= 1000) return words(n / 1000) + " Thousand " + words(n % 1000);
            if (n >= 100) return words(n / 100) + " Hundred " + words(n % 100);
            if (n >= 20) return TENS[(int) (n / 10)] + " " + ONES[(int) (n % 10)];
            return ONES[(int) n];
        }
    }
}
