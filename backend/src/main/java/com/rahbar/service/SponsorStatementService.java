package com.rahbar.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.util.UserRoles;
import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import com.rahbar.dto.EmailAttachment;

/**
 * The yearly statement for a sponsor (calendar year, January-December): for each of their students the
 * installments due in the year, what was paid, what is still outstanding, and the latest progress report.
 * Sponsors download their own; the office can download or email any sponsor's.
 */
@Service
public class SponsorStatementService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final Color HEAD = new Color(0xEE, 0xF2, 0xEC);

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentInstallmentRepository installmentRepository;
    private final StudentProgressRepository progressRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final EmailService emailService;

    public SponsorStatementService(UserRepository userRepository, PaymentRepository paymentRepository,
                                   PaymentInstallmentRepository installmentRepository, StudentProgressRepository progressRepository,
                                   StudentInstitutionCourseRepository studentCourseRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.installmentRepository = installmentRepository;
        this.progressRepository = progressRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.emailService = emailService;
    }

    /** The application / family details (father's name and profession, address, mobile numbers). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.GranteeDetailsRepository granteeDetailsRepository;

    /**
     * One student's line on the statement, in installments (amounts are never shown), with a few details about the
     * student for the sponsor (family: their application / family details, may be null).
     */
    record Line(User student, String course, int dueCount, int paidCount, int outstanding, StudentProgress latestProgress,
                com.rahbar.entity.GranteeDetails family) {}

    /** The sponsor's statement for the year as a PDF. */
    @Transactional(readOnly = true)
    public byte[] pdf(Long sponsorId, int year) {
        User sponsor = requireSponsor(sponsorId);
        checkYear(year);
        LocalDate from = LocalDate.of(year, 1, 1), to = LocalDate.of(year, 12, 31);
        LocalDate asOf = LocalDate.now().isBefore(to) ? LocalDate.now() : to;

        // Payments this sponsor made in the year (Paid only).
        List<Payment> payments = paymentRepository.findByGrantorIdOrderByPaymentDateDesc(sponsorId).stream()
                .filter(p -> "Paid".equalsIgnoreCase(p.getStatus()) && p.getPaymentDate() != null)
                .filter(p -> !p.getPaymentDate().toLocalDate().isBefore(from) && !p.getPaymentDate().toLocalDate().isAfter(to))
                .sorted(Comparator.comparing(Payment::getPaymentDate))
                .toList();
        // Installments between this sponsor and their students that fall in the year; which installment each payment paid.
        List<PaymentInstallment> all = installmentRepository.findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(sponsorId);
        List<PaymentInstallment> installments = all.stream()
                .filter(i -> !i.getDueDate().isBefore(from) && !i.getDueDate().isAfter(to)).toList();
        Map<Long, Integer> installmentNo = new HashMap<>();
        all.forEach(i -> { if (i.getPaymentId() != null) installmentNo.put(i.getPaymentId(), i.getInstallmentNo()); });

        Set<Long> studentIds = new LinkedHashSet<>();
        installments.forEach(i -> studentIds.add(i.getGranteeId()));
        payments.forEach(p -> studentIds.add(p.getGranteeId()));
        Map<Long, User> students = new HashMap<>();
        userRepository.findAllById(studentIds).forEach(u -> students.put(u.getId(), u));
        Map<Long, StudentProgress> latest = new HashMap<>();
        if (!studentIds.isEmpty()) {
            for (StudentProgress sp : progressRepository.findByGranteeIdInOrderByCreatedAtDesc(new ArrayList<>(studentIds))) {
                if (sp.getCreatedAt() == null || sp.getCreatedAt().getYear() != year) continue;
                latest.putIfAbsent(sp.getGranteeId(), sp);
            }
        }

        List<Line> lines = new ArrayList<>();
        for (Long id : studentIds) {
            User s = students.get(id);
            if (s == null) continue;
            List<PaymentInstallment> mine = installments.stream().filter(i -> i.getGranteeId().equals(id)).toList();
            List<Payment> paid = payments.stream().filter(p -> p.getGranteeId().equals(id)).toList();
            int outstanding = (int) mine.stream().filter(i -> i.isOpen() && !i.getDueDate().isAfter(asOf)).count();
            Map<String, Object> course = Rows.first(studentCourseRepository.findCourseDetails(id));
            String courseText = course == null ? "" : course.get("course_name") + " - " + course.get("institution_name");
            lines.add(new Line(s, courseText, mine.size(), paid.size(), outstanding, latest.get(id), familyOf(id)));
        }
        lines.sort(Comparator.comparing(l -> String.valueOf(l.student().getName()), String.CASE_INSENSITIVE_ORDER));
        try {
            return render(sponsor, year, asOf, lines, payments, students, installmentNo);
        } catch (DocumentException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create the statement.");
        }
    }

    /** Emails the statement to the sponsor as a PDF attachment; returns false when they have no usable email. */
    public boolean email(Long sponsorId, int year) {
        User sponsor = requireSponsor(sponsorId);
        String email = sponsor.getEmail();
        if (email == null || !email.contains("@") || email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com")) return false;
        byte[] pdf = pdf(sponsorId, year);
        Path file = null;
        try {
            file = Files.createTempFile("rahbar-statement-", ".pdf");
            Files.write(file, pdf);
            Map<String, Object> values = new HashMap<>();
            values.put("name", sponsor.getName());
            values.put("year", year);
            return emailService.send(EmailType.SPONSOR_STATEMENT, email, values,
                    List.of(new EmailAttachment(fileName(sponsor, year), file)));
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare the statement attachment.");
        } finally {
            if (file != null) try { Files.deleteIfExists(file); } catch (java.io.IOException ignored) { }
        }
    }

    public static String fileName(User sponsor, int year) {
        return "Rahbar_statement_" + year + "_" + sponsor.getUserId().replaceAll("[^A-Za-z0-9_-]", "") + ".pdf";
    }

    public User requireSponsor(Long id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found."));
        if (!UserRoles.hasAny(u, ServiceSupport.SPONSOR_ROLES, userRepository)) throw new ApiException(HttpStatus.BAD_REQUEST, "That user is not a sponsor.");
        return u;
    }

    private static void checkYear(int year) {
        if (year < 2000 || year > LocalDate.now().getYear()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a year up to this year.");
    }

    // ------------------------------------------------------------------------------------ PDF

    private static String installments(int n) {
        return n + (n == 1 ? " installment" : " installments");
    }

    /** The student's latest application / family details, if any. */
    private com.rahbar.entity.GranteeDetails familyOf(Long studentId) {
        if (granteeDetailsRepository == null) return null;
        return granteeDetailsRepository.findByUserId(studentId).stream()
                .max(Comparator.comparing(com.rahbar.entity.GranteeDetails::getGranteeDetailId)).orElse(null);
    }

    /** "-" for nothing. */
    private static String or(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.strip();
        return "-";
    }

    static byte[] render(User sponsor, int year, LocalDate asOf, List<Line> lines, List<Payment> payments,
                                 Map<Long, User> students, Map<Long, Integer> installmentNo) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x3D, 0x6B, 0x1F));
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font text = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        doc.add(new Paragraph("Rahbar - Bihar Anjuman", brand));
        doc.add(new Paragraph("Sponsorship statement " + year + " (1 Jan - 31 Dec " + year + ")", title));
        doc.add(new Paragraph("Sponsor: " + sponsor.getName() + " (" + sponsor.getUserId() + ")", text));
        doc.add(new Paragraph("Generated on " + LocalDate.now().format(DAY)
                + (asOf.getYear() == year && asOf.isBefore(LocalDate.of(year, 12, 31)) ? ". Outstanding installments are as of " + asOf.format(DAY) + "." : "."), small));
        doc.add(new Paragraph(" "));

        int totalDue = lines.stream().mapToInt(Line::dueCount).sum();
        int totalPaid = lines.stream().mapToInt(Line::paidCount).sum();
        int totalOutstanding = lines.stream().mapToInt(Line::outstanding).sum();
        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        for (String h : List.of("Students", "Due in " + year, "Paid in " + year, "Outstanding")) summary.addCell(head(h, bold));
        summary.addCell(cell(String.valueOf(lines.size()), text));
        summary.addCell(cell(installments(totalDue), text));
        summary.addCell(cell(installments(totalPaid), text));
        summary.addCell(cell(installments(totalOutstanding), text));
        doc.add(summary);
        doc.add(new Paragraph(" "));

        doc.add(heading(new Paragraph("Students", h2)));
        if (lines.isEmpty()) {
            doc.add(new Paragraph("No sponsored students or payments in " + year + ".", text));
        } else {
            PdfPTable t = new PdfPTable(new float[]{3.2f, 3.2f, 2f, 2f, 1.8f, 2.6f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String h : List.of("Student", "Course", "Due in year", "Paid in year", "Outstanding", "Latest progress report"))
                t.addCell(head(h, bold));
            for (Line l : lines) {
                t.addCell(cell(l.student().getName() + "\n" + l.student().getUserId(), text));
                t.addCell(cell(l.course(), text));
                t.addCell(cell(installments(l.dueCount()), text));
                t.addCell(cell(installments(l.paidCount()), text));
                t.addCell(cell(installments(l.outstanding()), text));
                StudentProgress p = l.latestProgress();
                t.addCell(cell(p == null ? "None uploaded in " + year
                        : (p.getSession() == null ? "" : p.getSession() + " ") + "(year " + p.getYear() + "): " + p.getMarks()
                        + (p.getReviewStatus() == null ? "" : " - " + p.getReviewStatus()), text));
            }
            doc.add(t);
        }
        doc.add(new Paragraph(" "));

        if (!lines.isEmpty()) {
            doc.add(heading(new Paragraph("Student details", h2)));
            PdfPTable t = new PdfPTable(new float[]{3f, 3.4f, 3f, 3.6f, 1.6f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String h : List.of("Student", "Phone / email", "Father (profession)", "Address", "Study status")) t.addCell(head(h, bold));
            for (Line l : lines) {
                User s = l.student();
                com.rahbar.entity.GranteeDetails f = l.family();
                t.addCell(cell(s.getName() + "\n" + s.getUserId(), text));
                t.addCell(cell(or(s.getPhone(), f == null ? null : f.getStudentMobile()) + "\n" + or(s.getEmail()), text));
                String father = f == null ? "-" : or(f.getFatherName());
                String profession = f == null ? null : f.getFatherProfession();
                t.addCell(cell(father + (profession == null || profession.isBlank() ? "" : "\n(" + profession.strip() + ")"), text));
                t.addCell(cell(f == null ? "-" : or(f.getAddress()), text));
                t.addCell(cell(StudyStatus.label(s.getStudyStatus()), text));
            }
            doc.add(t);
            doc.add(new Paragraph(" "));
        }

        doc.add(heading(new Paragraph("Payments in " + year, h2)));
        if (payments.isEmpty()) {
            doc.add(new Paragraph("No payments recorded in " + year + ".", text));
        } else {
            PdfPTable t = new PdfPTable(new float[]{2f, 4f, 2f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String h : List.of("Date", "Student", "Installment")) t.addCell(head(h, bold));
            for (Payment p : payments) {
                User s = students.get(p.getGranteeId());
                t.addCell(cell(p.getPaymentDate().toLocalDate().format(DAY), text));
                t.addCell(cell(s == null ? "" : s.getName() + " (" + s.getUserId() + ")", text));
                Integer no = installmentNo.get(p.getPaymentId());
                t.addCell(cell(no == null ? "-" : "Installment " + no, text));
            }
            doc.add(t);
        }
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Thank you for supporting our students. For any question about this statement, please contact the Rahbar office.", small));
        doc.close();
        return out.toByteArray();
    }

    private static Paragraph heading(Paragraph p) {
        p.setSpacingAfter(6);
        return p;
    }

    private static PdfPCell head(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setBackgroundColor(HEAD);
        c.setPadding(5);
        return c;
    }

    private static PdfPCell cell(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s == null ? "" : s, f));
        c.setPadding(5);
        return c;
    }
}
