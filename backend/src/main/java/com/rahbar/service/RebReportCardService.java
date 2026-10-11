package com.rahbar.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.dto.RebPdf;
import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.RebClassScope;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;

import static com.rahbar.util.RebSupport.bad;
import static com.rahbar.util.RebSupport.notFound;

/**
 * Report cards of a published REB exam: one page per girl, with the Madrasa subjects and the School subjects in two
 * blocks (marks, maximum, grade of each subject), the totals, percentage, grade, rank, result, attendance and the
 * class teacher's remark. For one girl or a whole section.
 */
@Service
public class RebReportCardService {

    private static final Color GREEN = new Color(0x0E, 0x6B, 0x47);
    private static final Color LINE = new Color(0xD6, 0xE0, 0xDA);
    private static final Color HEAD = new Color(0xE2, 0xF0, 0xE9);

    private final RebExamService examService;
    private final RebExamRepository exams;
    private final RebEnrollmentRepository enrollments;
    private final RebStudentRepository students;
    private final RebSessionRepository sessions;
    private final RebSetupService setup;
    private final RebStudentService studentService;
    private final RebClassScope classScope;

    public RebReportCardService(RebExamService examService, RebExamRepository exams, RebEnrollmentRepository enrollments,
                                RebStudentRepository students, RebSessionRepository sessions, RebSetupService setup,
                                RebStudentService studentService, RebClassScope classScope) {
        this.examService = examService;
        this.exams = exams;
        this.enrollments = enrollments;
        this.students = students;
        this.sessions = sessions;
        this.setup = setup;
        this.studentService = studentService;
        this.classScope = classScope;
    }

    public RebPdf forSection(Long examId, Long sectionId) {
        RebExam e = published(examId);
        classScope.requireSection(sectionId);
        Map<String, Object> res = examService.results(examId, sectionId);
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) res.get("rows");
        if (rows.isEmpty()) throw bad("No girls in this class.");
        String label = String.valueOf(res.get("section"));
        return new RebPdf(render(e, label, rows), "Report_cards_" + clean(e.getName()) + "_" + clean(label) + ".pdf");
    }

    public RebPdf forStudent(Long examId, Long studentId) {
        RebExam e = published(examId);
        RebEnrollment en = enrollments.findByStudentIdAndSessionId(studentId, e.getSessionId())
                .orElseThrow(() -> notFound("Result"));
        classScope.requireSection(en.getSectionId());
        Map<String, Object> res = examService.results(examId, en.getSectionId());
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) res.get("rows");
        List<Map<String, Object>> mine = rows.stream().filter(r -> studentId.equals(r.get("student_id"))).toList();
        if (mine.isEmpty()) throw notFound("Result");
        RebStudent g = students.findById(studentId).orElseThrow(() -> notFound("Student"));
        return new RebPdf(render(e, String.valueOf(res.get("section")), mine), "Report_card_" + clean(e.getName()) + "_" + clean(g.getAdmissionNo()) + ".pdf");
    }

    private RebExam published(Long examId) {
        RebExam e = exams.findById(examId).orElseThrow(() -> notFound("Exam"));
        if (!"PUBLISHED".equals(e.getStatus())) throw bad("Report cards are printed once the results are published.");
        return e;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    @SuppressWarnings("unchecked")
    private byte[] render(RebExam e, String section, List<Map<String, Object>> rows) {
        Map<String, String> settings = setup.settingValues();
        String school = blankTo(settings.get("school_name"), "Rahbar-e-Banaat School-cum-Madarsa");
        String sessionName = sessions.findById(e.getSessionId()).map(RebSession::getName).orElse("");
        List<RebExamService.Band> scale = examService.bands(e.getSessionId());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, GREEN);
            Font sub = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font text = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font h = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, GREEN);
            boolean first = true;
            for (Map<String, Object> r : rows) {
                if (!first) doc.newPage();
                first = false;
                Paragraph p = new Paragraph(school, brand);
                p.setAlignment(Element.ALIGN_CENTER);
                doc.add(p);
                String address = settings.getOrDefault("school_address", "");
                if (!address.isBlank()) {
                    p = new Paragraph(address, sub);
                    p.setAlignment(Element.ALIGN_CENTER);
                    doc.add(p);
                }
                p = new Paragraph("REPORT CARD · " + e.getName() + " · Session " + sessionName, title);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingBefore(6);
                p.setSpacingAfter(10);
                doc.add(p);

                PdfPTable who = new PdfPTable(new float[]{1.3f, 3f, 1f, 1.6f});
                who.setWidthPercentage(100);
                cell(who, "Name", bold); cell(who, (String) r.get("name"), text); cell(who, "Class", bold); cell(who, section, text);
                cell(who, "Admission no.", bold); cell(who, (String) r.get("admission_no"), text); cell(who, "Roll no.", bold); cell(who, (String) r.get("roll_no"), text);
                doc.add(who);

                List<Map<String, Object>> papers = (List<Map<String, Object>>) r.get("papers");
                for (String track : List.of("MADRASA", "SCHOOL")) {
                    List<Map<String, Object>> block = papers.stream().filter(x -> track.equals(x.get("track"))).toList();
                    if (block.isEmpty()) continue;
                    p = new Paragraph("MADRASA".equals(track) ? "Madrasa subjects" : "School subjects", h);
                    p.setSpacingBefore(10);
                    p.setSpacingAfter(4);
                    doc.add(p);
                    PdfPTable t = new PdfPTable(new float[]{3.2f, 1.2f, 1.2f, 1.3f, 1f});
                    t.setWidthPercentage(100);
                    for (String col : List.of("Subject", "Maximum", "Pass", "Obtained", "Grade")) head(t, col, bold);
                    BigDecimal got = BigDecimal.ZERO, max = BigDecimal.ZERO;
                    for (Map<String, Object> x : block) {
                        BigDecimal m = (BigDecimal) x.get("marks");
                        boolean absent = Boolean.TRUE.equals(x.get("absent"));
                        int mx = (Integer) x.get("max_marks");
                        cell(t, (String) x.get("subject_name"), text);
                        cell(t, String.valueOf(mx), text);
                        cell(t, String.valueOf(x.get("pass_marks")), text);
                        cell(t, absent ? "Absent" : m == null ? "-" : plain(m), m != null && m.compareTo(BigDecimal.valueOf((Integer) x.get("pass_marks"))) < 0 ? bold : text);
                        RebExamService.Band b = m == null || mx == 0 ? null
                                : RebExamService.grade(scale, m.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(mx), 2, RoundingMode.HALF_UP));
                        cell(t, b == null ? "-" : b.grade(), text);
                        if (m != null) got = got.add(m);
                        max = max.add(BigDecimal.valueOf(mx));
                    }
                    cell(t, "Total", bold); cell(t, plain(max), bold); cell(t, "", text); cell(t, plain(got), bold); cell(t, "", text);
                    doc.add(t);
                }

                PdfPTable sum = new PdfPTable(new float[]{1.4f, 1.4f, 1f, 1f, 1.2f, 1.6f});
                sum.setWidthPercentage(100);
                sum.setSpacingBefore(12);
                for (String col : List.of("Total", "Percentage", "Grade", "Rank", "Result", "Attendance")) head(sum, col, bold);
                cell(sum, plain((BigDecimal) r.get("total")) + " / " + plain((BigDecimal) r.get("max_total")), text);
                cell(sum, r.get("percent") == null ? "-" : plain((BigDecimal) r.get("percent")) + "%", bold);
                cell(sum, (String) r.get("grade"), bold);
                cell(sum, r.get("rank") == null ? "-" : String.valueOf(r.get("rank")), text);
                cell(sum, r.get("result") == null ? "-" : r.get("result").equals("PASS") ? "Pass" : r.get("result").equals("FAIL") ? "Needs improvement" : "Absent", bold);
                cell(sum, r.get("attendance_percent") == null ? "-" : plain((BigDecimal) r.get("attendance_percent")) + "%", text);
                doc.add(sum);

                p = new Paragraph("Class teacher's remark: " + (r.get("teacher_remark") == null ? "" : r.get("teacher_remark")), text);
                p.setSpacingBefore(10);
                doc.add(p);
                PdfPTable sign = new PdfPTable(3);
                sign.setWidthPercentage(100);
                sign.setSpacingBefore(40);
                for (String s : List.of("Class teacher", "Principal / Nazima" + (settings.getOrDefault("principal_name", "").isBlank() ? "" : "\n" + settings.get("principal_name")), "Parent / Guardian")) {
                    PdfPCell c = new PdfPCell(new Phrase("______________________\n" + s, text));
                    c.setBorder(Rectangle.NO_BORDER);
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    sign.addCell(c);
                }
                doc.add(sign);
                StringBuilder key = new StringBuilder("Grades: ");
                for (RebExamService.Band b : scale) key.append(b.grade()).append(" from ").append(plain(b.min())).append("%  ");
                p = new Paragraph(key.toString().strip(), sub);
                p.setSpacingBefore(14);
                doc.add(p);
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Could not create the report cards", ex);
        }
    }

    private static String plain(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static void head(PdfPTable t, String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setBackgroundColor(HEAD);
        c.setBorderColor(LINE);
        c.setPadding(4);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s == null || s.isBlank() ? "-" : s, f));
        c.setBorderColor(LINE);
        c.setPadding(4);
        t.addCell(c);
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.strip();
    }
}
