package com.rahbar.service;

import com.rahbar.dto.RebPdf;
import com.rahbar.entity.RebEnrollment;
import com.rahbar.repository.RebEnrollmentRepository;
import com.rahbar.entity.RebGuardian;
import com.rahbar.repository.RebGuardianRepository;
import com.rahbar.entity.RebSession;
import com.rahbar.repository.RebSessionRepository;
import com.rahbar.entity.RebStudent;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static com.rahbar.util.RebSupport.*;

/**
 * Printable certificates of a girl: a transfer certificate (for a girl who has left or passed out) and a bonafide
 * certificate (for a girl studying now), with the school's name and address from REB Setup > Settings.
 */
@Service
public class RebCertificateService {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final Color GREEN = new Color(0x0E, 0x6B, 0x47);

    private final RebStudentService studentService;
    private final RebGuardianRepository guardians;
    private final RebEnrollmentRepository enrollments;
    private final RebSessionRepository sessions;
    private final RebSetupService setup;

    public RebCertificateService(RebStudentService studentService, RebGuardianRepository guardians,
                                 RebEnrollmentRepository enrollments, RebSessionRepository sessions, RebSetupService setup) {
        this.studentService = studentService;
        this.guardians = guardians;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.setup = setup;
    }

    /** type: TC or BONAFIDE. */
    public RebPdf certificate(Long studentId, String type) {
        RebStudent s = studentService.require(studentId);
        String kind = code(type, Set.of("TC", "BONAFIDE"), null, "certificate");
        if (kind == null) throw bad("Choose the certificate.");
        if ("TC".equals(kind) && "ACTIVE".equals(s.getStatus())) {
            throw bad("A transfer certificate is for a girl who has left or passed out. Change her status first.");
        }
        if ("BONAFIDE".equals(kind) && !"ACTIVE".equals(s.getStatus())) throw bad("A bonafide certificate is for a girl studying now.");

        List<RebGuardian> gs = guardians.findByStudentIdOrderByPrimaryDescIdAsc(studentId);
        String father = gs.stream().filter(g -> "FATHER".equals(g.getRelation())).map(RebGuardian::getName).findFirst().orElse(null);
        String mother = gs.stream().filter(g -> "MOTHER".equals(g.getRelation())).map(RebGuardian::getName).findFirst().orElse(null);
        String parent = father != null ? father : gs.isEmpty() ? null : gs.get(0).getName();

        // her latest enrollment: the class she is in / last studied in
        Map<Long, RebSession> sessionById = new HashMap<>();
        sessions.findAll().forEach(x -> sessionById.put(x.getId(), x));
        RebEnrollment last = enrollments.findByStudentId(studentId).stream()
                .max(Comparator.comparing(e -> sessionById.get(e.getSessionId()) == null ? LocalDate.MIN : sessionById.get(e.getSessionId()).getStartDate()))
                .orElse(null);
        String section = last == null ? null : studentService.sectionLabels().get(last.getSectionId());
        RebSession lastSession = last == null ? null : sessionById.get(last.getSessionId());

        Map<String, String> settings = setup.settingValues();
        String school = blankTo(settings.get("school_name"), "Rahbar-e-Banaat School-cum-Madarsa");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, GREEN);
            Font sub = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font text = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            Paragraph p = new Paragraph(school, brand);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
            String address = String.join(" · ", java.util.stream.Stream.of(settings.get("school_address"), settings.get("school_phone"))
                    .filter(v -> v != null && !v.isBlank()).toList());
            if (!address.isEmpty()) {
                p = new Paragraph(address, sub);
                p.setAlignment(Element.ALIGN_CENTER);
                doc.add(p);
            }
            p = new Paragraph("Run by Bihar Anjuman", sub);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(18);
            doc.add(p);
            p = new Paragraph("TC".equals(kind) ? "TRANSFER CERTIFICATE" : "BONAFIDE CERTIFICATE", title);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(16);
            doc.add(p);

            if ("TC".equals(kind)) {
                PdfPTable t = new PdfPTable(new float[]{2.2f, 3.8f});
                t.setWidthPercentage(100);
                row(t, "Admission number", s.getAdmissionNo(), bold, text);
                row(t, "Name of the student", s.getName(), bold, text);
                row(t, "Father's name", father, bold, text);
                row(t, "Mother's name", mother, bold, text);
                row(t, "Date of birth", s.getDateOfBirth() == null ? null : s.getDateOfBirth().format(DAY), bold, text);
                row(t, "Date of admission", s.getAdmittedOn() == null ? null : s.getAdmittedOn().format(DAY), bold, text);
                row(t, "Class last studied", section == null ? null : section + (lastSession == null ? "" : " (" + lastSession.getName() + ")"), bold, text);
                row(t, "Board registration number", s.getBoardRegistrationNo(), bold, text);
                row(t, "Date of leaving", s.getLeftOn() == null ? null : s.getLeftOn().format(DAY), bold, text);
                row(t, "Reason for leaving", "PASSED_OUT".equals(s.getStatus()) ? "Completed her studies" : s.getLeavingReason(), bold, text);
                row(t, "Conduct", "Good", bold, text);
                doc.add(t);
            } else {
                String body = "This is to certify that " + s.getName()
                        + (parent == null ? "" : ", daughter of " + parent + ",")
                        + " (admission number " + s.getAdmissionNo() + ") is a bonafide student of this institution"
                        + (section == null ? "" : ", studying in " + section)
                        + (lastSession == null ? "" : " in the session " + lastSession.getName()) + "."
                        + (s.getDateOfBirth() == null ? "" : " Her date of birth as per our records is " + s.getDateOfBirth().format(DAY) + ".")
                        + (s.isResidential() ? " She lives in the institution's hostel." : "");
                p = new Paragraph(body, text);
                p.setLeading(18);
                p.setAlignment(Element.ALIGN_JUSTIFIED);
                doc.add(p);
            }

            p = new Paragraph("Issued on " + LocalDate.now().format(DAY), text);
            p.setSpacingBefore(28);
            doc.add(p);
            p = new Paragraph("\n\n" + blankTo(settings.get("principal_name"), "") + "\nPrincipal / Nazima", bold);
            p.setAlignment(Element.ALIGN_RIGHT);
            doc.add(p);
            p = new Paragraph("Generated by Rahbar. Not valid without the signature and seal of the institution.", small);
            p.setSpacingBefore(30);
            doc.add(p);
            doc.close();
            String file = ("TC".equals(kind) ? "TC_" : "Bonafide_") + s.getAdmissionNo().replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf";
            return new RebPdf(out.toByteArray(), file);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not create the certificate", e);
        }
    }

    private static void row(PdfPTable t, String label, String value, Font bold, Font text) {
        PdfPCell a = new PdfPCell(new Phrase(label, bold));
        PdfPCell b = new PdfPCell(new Phrase(value == null || value.isBlank() ? "—" : value, text));
        for (PdfPCell c : List.of(a, b)) {
            c.setPadding(7);
            c.setBorderColor(new Color(0xD6, 0xE0, 0xDA));
        }
        t.addCell(a);
        t.addCell(b);
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.strip();
    }
}
