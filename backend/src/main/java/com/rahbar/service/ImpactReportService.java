package com.rahbar.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Impact reports, in counts only (no money is shown to anyone):
 * <ul>
 *   <li>Reports > Impact Report: the organisation's (a chapter lead: their chapter's) scholarship students of a
 *   session year or all years, what they study, how they are doing, graduates' outcomes and coaching results.</li>
 *   <li>Sponsor portal > My Impact: the students a sponsor has supported, now and before.</li>
 * </ul>
 * Each is a page and a PDF.
 */
@Service
public class ImpactReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Color GREEN = new Color(0x3D, 0x6B, 0x1F), HEAD = new Color(0xEE, 0xF2, 0xEC);
    static final int TOP = 8;

    private final UserRepository users;
    private final GrantorGranteeRepository mappings;
    private final SponsorMappingHistoryRepository history;
    private final StudentInstitutionCourseRepository studentCourses;
    private final CourseRepository courses;
    private final InstitutionRepository institutions;
    private final StudentProgressRepository progress;
    private final PaymentInstallmentRepository installments;
    private final AlumniProfileRepository alumni;
    private final StudentScope studentScope;
    private final CoachingStudentRepository coachingStudents;
    private final CoachingExamRepository coachingExams;
    private final CoachingExamPrizeRepository prizes;
    private final CoachingAttendanceRepository attendance;
    private final CoachingAccess coachingAccess;
    private final CoachingResults coachingResults;
    private final CoachingPipelineService pipeline;

    public ImpactReportService(UserRepository users, GrantorGranteeRepository mappings, SponsorMappingHistoryRepository history,
                               StudentInstitutionCourseRepository studentCourses, CourseRepository courses, InstitutionRepository institutions,
                               StudentProgressRepository progress, PaymentInstallmentRepository installments, AlumniProfileRepository alumni,
                               StudentScope studentScope, CoachingStudentRepository coachingStudents, CoachingExamRepository coachingExams,
                               CoachingExamPrizeRepository prizes, CoachingAttendanceRepository attendance, CoachingAccess coachingAccess,
                               CoachingResults coachingResults, CoachingPipelineService pipeline) {
        this.users = users;
        this.mappings = mappings;
        this.history = history;
        this.studentCourses = studentCourses;
        this.courses = courses;
        this.institutions = institutions;
        this.progress = progress;
        this.installments = installments;
        this.alumni = alumni;
        this.studentScope = studentScope;
        this.coachingStudents = coachingStudents;
        this.coachingExams = coachingExams;
        this.prizes = prizes;
        this.attendance = attendance;
        this.coachingAccess = coachingAccess;
        this.coachingResults = coachingResults;
        this.pipeline = pipeline;
    }

    // ================================================================ organisation

    /** year: a session year, or null for all years. */
    public Map<String, Object> organisation(Integer year) {
        StudentScope.Scope scope = studentScope.current();
        List<User> all = users.findByRoleId(ServiceSupport.STUDENT_ROLE).stream()
                .filter(u -> u.getMergedIntoId() == null && !DataQualityService.coachingOnly(u)).filter(scope::includes).toList();
        List<User> s = all.stream().filter(u -> year == null || year.equals(u.getYear())).toList();
        Set<Long> ids = s.stream().map(User::getId).collect(Collectors.toSet());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("years", all.stream().map(User::getYear).filter(Objects::nonNull).distinct().sorted(Comparator.reverseOrder()).toList());
        out.put("scope", scope.all() ? null : scope.label());
        out.put("generated_on", LocalDate.now());

        Map<String, Object> sch = new LinkedHashMap<>();
        sch.put("students", s.size());
        long girls = s.stream().filter(u -> "F".equalsIgnoreCase(u.getSex())).count();
        sch.put("girls", girls);
        sch.put("girls_percent", pct(girls, s.size()));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        StudyStatus.LABELS.forEach((code, label) -> byStatus.put(label, s.stream().filter(u -> code.equals(StudyStatus.of(u.getStudyStatus()))).count()));
        sch.put("by_study_status", byStatus);
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        Set<Long> sponsors = new HashSet<>();
        long sponsored = 0;
        for (GrantorGrantee g : mappings.findAll()) {
            if (ids.contains(g.getGranteeId()) && g.getGrantorId() != null && !g.getGrantorId().equals(unassigned)) {
                sponsored++;
                sponsors.add(g.getGrantorId());
            }
        }
        sch.put("sponsored_now", sponsored);
        sch.put("sponsors", sponsors.size());
        sch.put("by_chapter", top(s.stream().collect(Collectors.groupingBy(u -> ServiceSupport.chapterLabel(u.getChapterName()), Collectors.counting()))));

        Map<Long, String> courseNames = new HashMap<>();
        courses.findAll().forEach(c -> courseNames.put(c.getCourseId(), c.getCourseName()));
        Map<String, String> instNames = new HashMap<>();
        institutions.findAll().forEach(i -> instNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<String, Long> byCourse = new HashMap<>();
        Set<String> insts = new HashSet<>();
        for (StudentInstitutionCourse c : studentCourses.findAll()) {
            if (!ids.contains(c.getUserId())) continue;
            byCourse.merge(courseNames.getOrDefault(c.getCourseId(), "Other"), 1L, Long::sum);
            if (c.getInstitutionId() != null) insts.add(c.getInstitutionId());
        }
        sch.put("courses", top(byCourse));
        sch.put("institutions", insts.size());

        long paid = installments.findByGranteeIdInOrderByGranteeIdAscInstallmentNoAsc(ids.isEmpty() ? List.of(-1L) : ids).stream()
                .filter(i -> i.getPaymentId() != null).count();
        sch.put("installments_paid", paid);
        Map<Long, StudentProgress> latest = new HashMap<>();
        long reports = 0;
        for (StudentProgress p : progress.findAll()) {
            if (!ids.contains(p.getGranteeId())) continue;
            reports++;
            StudentProgress was = latest.get(p.getGranteeId());
            if (ServiceSupport.marks(p.getMarks()) != null && (was == null || (p.getCreatedAt() != null && was.getCreatedAt() != null && p.getCreatedAt().isAfter(was.getCreatedAt())))) {
                latest.put(p.getGranteeId(), p);
            }
        }
        sch.put("progress_reports", reports);
        sch.put("average_latest_marks", latest.isEmpty() ? null : BigDecimal.valueOf(latest.values().stream()
                .mapToDouble(p -> ServiceSupport.marks(p.getMarks())).average().orElse(0)).setScale(1, RoundingMode.HALF_UP));
        Set<Long> graduated = s.stream().filter(u -> StudyStatus.GRADUATED.equals(StudyStatus.of(u.getStudyStatus()))).map(User::getId).collect(Collectors.toSet());
        Map<String, Long> outcomes = new LinkedHashMap<>();
        AlumniProfile.STATUSES.values().forEach(l -> outcomes.put(l, 0L));
        long unknown = graduated.size();
        for (AlumniProfile a : alumni.findAll()) {
            if (!graduated.contains(a.getUserId()) || a.getCurrentStatus() == null) continue;
            outcomes.merge(AlumniProfile.STATUSES.getOrDefault(a.getCurrentStatus(), "Other"), 1L, Long::sum);
            unknown--;
        }
        outcomes.put("Not known yet", unknown);
        sch.put("graduates", graduated.size());
        sch.put("graduate_outcomes", outcomes);
        out.put("scholarship", sch);

        Map<String, Object> coaching = coaching(year);
        if (coaching != null) out.put("coaching", coaching);
        return out;
    }

    /** Coaching centers the user may see: students admitted that year (all years: everyone), exams, prizes, attendance. */
    private Map<String, Object> coaching(Integer year) {
        Set<Long> centers = coachingAccess.centerIds();
        if (centers.isEmpty()) return null;
        List<CoachingStudent> list = coachingStudents.findAll().stream().filter(c -> centers.contains(c.getRccCenterId()))
                .filter(c -> year == null || (c.getAdmissionDate() != null && c.getAdmissionDate().getYear() == year)).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("centers", centers.size());
        out.put("students", list.size());
        out.put("studying_now", list.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count());
        long girls = list.stream().filter(c -> "F".equals(c.getGender())).count();
        out.put("girls_percent", pct(girls, list.size()));
        Map<String, Long> byClass = new LinkedHashMap<>();
        CoachingService.CLASSES.forEach(k -> byClass.put("Class " + k, list.stream().filter(c -> k.equals(c.getClassLevel())).count()));
        out.put("by_class", byClass);
        List<CoachingExam> exams = coachingExams.findAll().stream().filter(e -> centers.contains(e.getRccCenterId()))
                .filter(e -> year == null || e.getExamDate().getYear() == year).toList();
        out.put("exams", exams.size());
        Set<Long> examIds = exams.stream().map(CoachingExam::getId).collect(Collectors.toSet());
        out.put("prizes", examIds.isEmpty() ? 0 : prizes.findByExamIdIn(examIds).size());
        Map<Long, CoachingResults.StudentClass> classes = new HashMap<>();
        list.forEach(c -> classes.put(c.getId(), new CoachingResults.StudentClass(c.getRccCenterId(), c.getClassLevel(), c.getAdmissionDate())));
        List<BigDecimal> results = coachingResults.of(classes).values().stream().flatMap(List::stream)
                .filter(r -> examIds.contains(r.examId()) && r.percent() != null).map(CoachingResults.ExamResult::percent).toList();
        out.put("average_exam_percent", results.isEmpty() ? null : results.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(results.size()), 1, RoundingMode.HALF_UP));
        LocalDate from = year == null ? LocalDate.of(2000, 1, 1) : LocalDate.of(year, 1, 1), to = year == null ? LocalDate.now() : LocalDate.of(year, 12, 31);
        List<CoachingAttendance> att = attendance.findByAttDateBetween(from, to).stream().filter(a -> centers.contains(a.getRccCenterId())).toList();
        out.put("attendance_percent", CoachingAttendanceService.percent(att.stream().filter(a -> "P".equals(a.getStatus())).count(),
                att.stream().filter(a -> "A".equals(a.getStatus())).count()));
        List<CoachingStudent> ten = list.stream().filter(c -> c.getClassLevel() == CoachingPipelineService.SCHOLARSHIP_CLASS).toList();
        Map<Long, ScholarshipApplication> apps = pipeline.link(ten);
        out.put("class_10", ten.size());
        out.put("class_10_applied", apps.size());
        out.put("class_10_accepted", apps.values().stream().filter(a -> ScholarshipApplication.ACCEPTED.equals(a.getStatus())).count());
        return out;
    }

    // ================================================================ one sponsor

    /** The students the sponsor supports now and supported before, with how each is doing. */
    public Map<String, Object> sponsor(Long sponsorId) {
        User sponsor = ServiceSupport.requireUser(users, sponsorId, "Sponsor not found");
        Set<Long> current = new LinkedHashSet<>();
        mappings.findByGrantorId(sponsorId).forEach(g -> current.add(g.getGranteeId()));
        Map<Long, LocalDateTime[]> periods = new LinkedHashMap<>();
        for (SponsorMappingHistory h : history.findByGrantorIdOrderByStartedAtDesc(sponsorId)) {
            periods.merge(h.getGranteeId(), new LocalDateTime[]{h.getStartedAt(), h.getEndedAt()}, (a, b) -> new LocalDateTime[]{
                    a[0] == null || (b[0] != null && b[0].isBefore(a[0])) ? b[0] : a[0],
                    a[1] == null || b[1] == null ? null : (b[1].isAfter(a[1]) ? b[1] : a[1])});
        }
        Map<Long, Long> paidBySponsor = new HashMap<>();
        for (PaymentInstallment i : installments.findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(sponsorId)) {
            if (i.getPaymentId() != null) paidBySponsor.merge(i.getGranteeId(), 1L, Long::sum);
        }
        Set<Long> ids = new LinkedHashSet<>(current);
        ids.addAll(periods.keySet());
        ids.addAll(paidBySponsor.keySet());
        Map<Long, String> courseNames = new HashMap<>();
        courses.findAll().forEach(c -> courseNames.put(c.getCourseId(), c.getCourseName()));
        Map<String, String> instNames = new HashMap<>();
        institutions.findAll().forEach(i -> instNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, StudentInstitutionCourse> courseOf = new HashMap<>();
        studentCourses.findAll().forEach(c -> courseOf.put(c.getUserId(), c));
        Map<Long, AlumniProfile> alumniOf = new HashMap<>();
        alumni.findAll().forEach(a -> alumniOf.put(a.getUserId(), a));
        Map<Long, List<StudentProgress>> reports = new HashMap<>();
        for (StudentProgress p : progress.findAll()) {
            if (ids.contains(p.getGranteeId()) && ServiceSupport.marks(p.getMarks()) != null) reports.computeIfAbsent(p.getGranteeId(), k -> new ArrayList<>()).add(p);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDateTime since = null;
        for (User u : users.findAllById(ids)) {
            boolean now = current.contains(u.getId());
            LocalDateTime[] p = periods.get(u.getId());
            if (p != null && p[0] != null && (since == null || p[0].isBefore(since))) since = p[0];
            StudentInstitutionCourse c = courseOf.get(u.getId());
            List<StudentProgress> r = reports.getOrDefault(u.getId(), List.of());
            StudentProgress last = r.stream().max(Comparator.comparing(StudentProgress::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
            AlumniProfile a = alumniOf.get(u.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getName());
            row.put("user_id", u.getUserId());
            row.put("current", now);
            row.put("course", c == null ? null : courseNames.get(c.getCourseId()));
            row.put("institution", c == null ? null : instNames.get(c.getInstitutionId()));
            row.put("study_status", StudyStatus.label(u.getStudyStatus()));
            row.put("from", p == null ? null : p[0]);
            row.put("until", now || p == null ? null : p[1]);
            row.put("installments_paid", paidBySponsor.getOrDefault(u.getId(), 0L));
            row.put("latest_marks", last == null ? null : last.getMarks());
            row.put("now_doing", a == null || a.getCurrentStatus() == null ? null : AlumniProfile.STATUSES.get(a.getCurrentStatus())
                    + (a.getOrganisation() == null || a.getOrganisation().isBlank() ? "" : " at " + a.getOrganisation()));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> !Boolean.TRUE.equals(r.get("current"))).thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("students_supported", rows.size());
        totals.put("studying_now", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("current")) && StudyStatus.label(StudyStatus.STUDYING).equals(r.get("study_status"))).count());
        totals.put("graduated", rows.stream().filter(r -> StudyStatus.label(StudyStatus.GRADUATED).equals(r.get("study_status"))).count());
        totals.put("installments_paid", paidBySponsor.values().stream().mapToLong(Long::longValue).sum());
        totals.put("sponsoring_since", since == null ? null : since.toLocalDate());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sponsor", Map.of("name", sponsor.getName(), "user_id", sponsor.getUserId() == null ? "" : sponsor.getUserId()));
        out.put("totals", totals);
        out.put("students", rows);
        out.put("generated_on", LocalDate.now());
        return out;
    }

    // ================================================================ PDFs

    @SuppressWarnings("unchecked")
    public byte[] organisationPdf(Integer year) {
        Map<String, Object> d = organisation(year);
        Map<String, Object> s = (Map<String, Object>) d.get("scholarship");
        Pdf pdf = new Pdf();
        pdf.title("Impact report " + (year == null ? "(all years)" : "– session " + year)
                + (d.get("scope") == null ? "" : " · " + d.get("scope")));
        pdf.text("Bihar Anjuman supports and promotes the education of students from marginalised communities in Bihar.");
        pdf.h2("Rahbar scholarship");
        pdf.stats(new String[][]{{"Students", n(s.get("students"))}, {"Girls", n(s.get("girls")) + " (" + n(s.get("girls_percent")) + "%)"},
                {"Sponsored now", n(s.get("sponsored_now"))}, {"Sponsors", n(s.get("sponsors"))},
                {"Institutions", n(s.get("institutions"))}, {"Installments paid", n(s.get("installments_paid"))},
                {"Progress reports", n(s.get("progress_reports"))}, {"Average latest marks", s.get("average_latest_marks") == null ? "-" : s.get("average_latest_marks") + "%"},
                {"Graduates", n(s.get("graduates"))}});
        pdf.table("Where they are in their studies", (Map<String, Long>) s.get("by_study_status"), "Students");
        pdf.table("What they study", (Map<String, Long>) s.get("courses"), "Students");
        pdf.table("By chapter", (Map<String, Long>) s.get("by_chapter"), "Students");
        pdf.table("What our graduates do now", (Map<String, Long>) s.get("graduate_outcomes"), "Graduates");
        Map<String, Object> c = (Map<String, Object>) d.get("coaching");
        if (c != null) {
            pdf.h2("Coaching centers (classes 7–10)");
            pdf.stats(new String[][]{{"Centers", n(c.get("centers"))}, {"Students" + (year == null ? "" : " admitted"), n(c.get("students"))},
                    {"Studying now", n(c.get("studying_now"))}, {"Girls", n(c.get("girls_percent")) + "%"},
                    {"Exams held", n(c.get("exams"))}, {"Average exam marks", c.get("average_exam_percent") == null ? "-" : c.get("average_exam_percent") + "%"},
                    {"Prizes awarded", n(c.get("prizes"))}, {"Attendance", c.get("attendance_percent") == null ? "-" : c.get("attendance_percent") + "%"},
                    {"Class 10 → applied for Rahbar", n(c.get("class_10_applied")) + " of " + n(c.get("class_10"))}});
            pdf.table("Students by class", (Map<String, Long>) c.get("by_class"), "Students");
        }
        pdf.small("Generated on " + LocalDate.now().format(DAY) + ". Counts only; this report shows no amounts.");
        return pdf.done();
    }

    @SuppressWarnings("unchecked")
    public byte[] sponsorPdf(Long sponsorId) {
        Map<String, Object> d = sponsor(sponsorId);
        Map<String, Object> t = (Map<String, Object>) d.get("totals");
        Map<String, Object> sp = (Map<String, Object>) d.get("sponsor");
        Pdf pdf = new Pdf();
        pdf.title("Your impact with Rahbar");
        pdf.text(sp.get("name") + (String.valueOf(sp.get("user_id")).isBlank() ? "" : " (" + sp.get("user_id") + ")")
                + (t.get("sponsoring_since") == null ? "" : " · sponsoring since " + ((LocalDate) t.get("sponsoring_since")).format(DAY)));
        pdf.stats(new String[][]{{"Students supported", n(t.get("students_supported"))}, {"Studying now", n(t.get("studying_now"))},
                {"Graduated", n(t.get("graduated"))}, {"Installments paid", n(t.get("installments_paid"))}});
        pdf.h2("Your students");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) d.get("students");
        if (rows.isEmpty()) {
            pdf.text("No students yet.");
        } else {
            PdfPTable table = new PdfPTable(new float[]{3f, 3.4f, 1.8f, 1.4f, 1.2f, 3f});
            table.setWidthPercentage(100);
            for (String h : new String[]{"Student", "Course", "Status", "Installments", "Latest marks", "Now"}) table.addCell(pdf.head(h));
            for (Map<String, Object> r : rows) {
                table.addCell(pdf.cell(r.get("name") + (Boolean.TRUE.equals(r.get("current")) ? "" : " (earlier)")));
                table.addCell(pdf.cell(Objects.toString(r.get("course"), "-") + (r.get("institution") == null ? "" : ", " + r.get("institution"))));
                table.addCell(pdf.cell(Objects.toString(r.get("study_status"), "")));
                table.addCell(pdf.cell(n(r.get("installments_paid"))));
                table.addCell(pdf.cell(r.get("latest_marks") == null ? "-" : r.get("latest_marks") + "%"));
                table.addCell(pdf.cell(Objects.toString(r.get("now_doing"), "")));
            }
            pdf.add(table);
        }
        pdf.small("Thank you for supporting our students. Generated on " + LocalDate.now().format(DAY) + ".");
        return pdf.done();
    }

    /** A small A4 PDF in the Rahbar style. */
    private static final class Pdf {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        private final Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, GREEN);
        private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        private final Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, GREEN);
        private final Font text = FontFactory.getFont(FontFactory.HELVETICA, 9);
        private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        private final Font big = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        private final Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        Pdf() {
            PdfWriter.getInstance(doc, out);
            doc.open();
            add(new Paragraph("Rahbar - Bihar Anjuman", brand));
        }

        void title(String s) { add(new Paragraph(s, title)); }
        void text(String s) { add(new Paragraph(s, text)); }
        void small(String s) { add(new Paragraph(" ")); add(new Paragraph(s, small)); }

        void h2(String s) {
            Paragraph p = new Paragraph(s, h2);
            p.setSpacingBefore(12);
            p.setSpacingAfter(6);
            add(p);
        }

        /** Numbers in a grid of three: label above, the number below. */
        void stats(String[][] items) {
            PdfPTable t = new PdfPTable(3);
            t.setWidthPercentage(100);
            t.setSpacingBefore(6);
            for (String[] i : items) {
                PdfPCell c = new PdfPCell();
                c.setPadding(6);
                c.setBorderColor(HEAD);
                c.addElement(new Paragraph(i[0], small));
                c.addElement(new Paragraph(i[1], big));
                t.addCell(c);
            }
            for (int k = items.length % 3; k != 0 && k < 3; k++) { PdfPCell c = new PdfPCell(); c.setBorder(0); t.addCell(c); }
            add(t);
        }

        /** A heading and a two-column table of counts (rows with 0 left out). */
        void table(String heading, Map<String, Long> rows, String what) {
            if (rows == null || rows.values().stream().allMatch(v -> v == 0)) return;
            Paragraph p = new Paragraph(heading, bold);
            p.setSpacingBefore(8);
            p.setSpacingAfter(4);
            add(p);
            PdfPTable t = new PdfPTable(new float[]{5f, 1.5f});
            t.setWidthPercentage(70);
            t.setHorizontalAlignment(Element.ALIGN_LEFT);
            t.addCell(head(""));
            t.addCell(head(what));
            rows.forEach((k, v) -> { if (v > 0) { t.addCell(cell(k)); t.addCell(cell(String.valueOf(v))); } });
            add(t);
        }

        PdfPCell head(String s) {
            PdfPCell c = new PdfPCell(new Phrase(s, bold));
            c.setBackgroundColor(HEAD);
            c.setPadding(5);
            return c;
        }

        PdfPCell cell(String s) {
            PdfPCell c = new PdfPCell(new Phrase(s == null ? "" : s, text));
            c.setPadding(5);
            return c;
        }

        void add(Element e) {
            try {
                doc.add(e);
            } catch (DocumentException ex) {
                throw new IllegalStateException(ex);
            }
        }

        byte[] done() {
            doc.close();
            return out.toByteArray();
        }
    }

    // ================================================================ helpers

    /** The largest groups (at most TOP), largest first; the rest as "Others". */
    static Map<String, Long> top(Map<String, Long> counts) {
        List<Map.Entry<String, Long>> sorted = counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList();
        Map<String, Long> out = new LinkedHashMap<>();
        long others = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < TOP) out.put(sorted.get(i).getKey(), sorted.get(i).getValue()); else others += sorted.get(i).getValue();
        }
        if (others > 0) out.put("Others", others);
        return out;
    }

    static BigDecimal pct(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static String n(Object v) {
        return v == null ? "0" : v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(v);
    }
}
