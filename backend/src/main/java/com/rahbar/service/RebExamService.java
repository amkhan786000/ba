package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.RebClassScope;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.util.RebSupport.*;
import com.rahbar.dto.RebScaleBand;

/**
 * REB exams: an exam of a session has a date sheet (one paper per subject per class, from the class's
 * curriculum), marks per girl per paper, and results. Status: DRAFT (set the date sheet) -> MARKS_ENTRY (teachers
 * enter marks; with the "own REB classes" scope only for the subjects they teach) -> PUBLISHED (results kept as they
 * were, marks locked; can be unpublished for corrections). Results have a Madrasa block and a School block,
 * a percentage, a grade from the session's scale, a rank in the section and PASS / FAIL (any paper below its pass
 * marks, or absent, is a fail). The annual result combines the published exams by their weight.
 */
@Service
public class RebExamService {

    static final Set<String> KINDS = Set.of("UNIT_TEST", "TERM", "ANNUAL", "ORAL");

    private final RebExamRepository exams;
    private final RebExamPaperRepository papers;
    private final RebMarkRepository marks;
    private final RebExamResultRepository results;
    private final RebGradeBandRepository bands;
    private final RebClassSubjectRepository classSubjects;
    private final RebSubjectRepository subjects;
    private final RebClassRepository classes;
    private final RebSectionRepository sections;
    private final RebEnrollmentRepository enrollments;
    private final RebStudentRepository students;
    private final RebSetupService setup;
    private final RebStudentService studentService;
    private final RebAttendanceService attendance;
    private final RebClassScope classScope;

    public RebExamService(RebExamRepository exams, RebExamPaperRepository papers, RebMarkRepository marks, RebExamResultRepository results,
                          RebGradeBandRepository bands, RebClassSubjectRepository classSubjects, RebSubjectRepository subjects,
                          RebClassRepository classes, RebSectionRepository sections, RebEnrollmentRepository enrollments,
                          RebStudentRepository students, RebSetupService setup, RebStudentService studentService,
                          RebAttendanceService attendance, RebClassScope classScope) {
        this.exams = exams;
        this.papers = papers;
        this.marks = marks;
        this.results = results;
        this.bands = bands;
        this.classSubjects = classSubjects;
        this.subjects = subjects;
        this.classes = classes;
        this.sections = sections;
        this.enrollments = enrollments;
        this.students = students;
        this.setup = setup;
        this.studentService = studentService;
        this.attendance = attendance;
        this.classScope = classScope;
    }

    RebExam require(Long id) {
        return exams.findById(id).orElseThrow(() -> notFound("Exam"));
    }

    // ---------------------------------------------------------------- grading scale

    static final List<RebScaleBand> DEFAULT_BANDS = List.of(
            new RebScaleBand(new BigDecimal("90"), "A+", "Outstanding", true), new RebScaleBand(new BigDecimal("75"), "A", "Excellent", true),
            new RebScaleBand(new BigDecimal("60"), "B", "Very good", true), new RebScaleBand(new BigDecimal("45"), "C", "Good", true),
            new RebScaleBand(new BigDecimal("33"), "D", "Satisfactory", true), new RebScaleBand(BigDecimal.ZERO, "E", "Needs improvement", false));

    public List<RebScaleBand> bands(Long sessionId) {
        List<RebGradeBand> list = bands.findBySessionIdOrderByMinPercentDesc(sessionId);
        if (list.isEmpty()) return DEFAULT_BANDS;
        return list.stream().map(b -> new RebScaleBand(b.getMinPercent(), b.getGrade(), b.getRemark(), b.isPass())).toList();
    }

    static RebScaleBand grade(List<RebScaleBand> scale, BigDecimal percent) {
        if (percent == null) return null;
        for (RebScaleBand b : scale) if (percent.compareTo(b.min()) >= 0) return b;
        return scale.get(scale.size() - 1);
    }

    public Map<String, Object> gradeScale(Long sessionId) {
        RebSession s = setup.session(sessionId);
        boolean custom = !bands.findBySessionIdOrderByMinPercentDesc(s.getId()).isEmpty();
        return Map.of("session", Rows.of(s), "custom", custom, "bands", bands(s.getId()));
    }

    /** Body: { sessionId, bands: [{ min, grade, remark, pass }] }. Replaces the session's scale (must include 0). */
    @Transactional
    public void saveGradeScale(Map<String, Object> body) {
        RebSession s = setup.session(id(body.get("sessionId")));
        if (!(body.get("bands") instanceof List<?> list) || list.isEmpty()) throw bad("Send the grades.");
        List<RebGradeBand> out = new ArrayList<>();
        Set<String> grades = new HashSet<>();
        boolean zero = false;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid grade.");
            RebGradeBand b = new RebGradeBand();
            b.setSessionId(s.getId());
            BigDecimal min = money(m.get("min"), "From %");
            if (min == null || min.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("Each grade needs a starting percentage from 0 to 100.");
            b.setMinPercent(min);
            b.setGrade(text(m.get("grade"), "Grade", 5, true));
            if (!grades.add(b.getGrade())) throw bad("Grade " + b.getGrade() + " is listed twice.");
            b.setRemark(text(m.get("remark"), "Remark", 60, false));
            b.setPass(flag(m.get("pass"), true));
            zero |= min.signum() == 0;
            out.add(b);
        }
        if (!zero) throw bad("One grade must start at 0%.");
        bands.deleteAll(bands.findBySessionIdOrderByMinPercentDesc(s.getId()));
        bands.saveAll(out);
    }

    // ---------------------------------------------------------------- exams

    public List<Map<String, Object>> list(Long sessionId) {
        RebSession s = setup.session(sessionId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebExam e : exams.findBySessionIdOrderByStartDateAscIdAsc(s.getId())) {
            Map<String, Object> r = Rows.of(e);
            r.put("papers", papers.findByExamId(e.getId()).size());
            rows.add(r);
        }
        return rows;
    }

    /** Body: { sessionId, name, kind, weightPercent, startDate, endDate }. */
    @Transactional
    public RebExam save(Long id, Map<String, Object> body) {
        RebExam e = id == null ? new RebExam() : require(id);
        if (id == null) e.setSessionId(setup.session(id(body.get("sessionId"))).getId());
        e.setName(text(body.get("name"), "Name", 100, true));
        e.setKind(code(body.get("kind"), KINDS, "TERM", "kind"));
        BigDecimal w = money(body.get("weightPercent"), "Weight");
        if (w != null && w.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("The weight can be at most 100%.");
        e.setWeightPercent(w == null ? BigDecimal.ZERO : w);
        e.setStartDate(date(body.get("startDate"), "Start date"));
        e.setEndDate(date(body.get("endDate"), "End date"));
        if (e.getStartDate() != null && e.getEndDate() != null && e.getEndDate().isBefore(e.getStartDate())) throw bad("The exam ends before it starts.");
        return exams.save(e);
    }

    @Transactional
    public void delete(Long id) {
        RebExam e = require(id);
        if (!"DRAFT".equals(e.getStatus())) throw bad("Only a draft exam can be deleted (marks may have been entered).");
        exams.delete(e);
    }

    /** Body: { status: DRAFT | MARKS_ENTRY | PUBLISHED }. Publishing keeps the results and locks the marks. */
    @Transactional
    public String setStatus(Long id, Map<String, Object> body) {
        RebExam e = require(id);
        String to = code(body.get("status"), Set.of("DRAFT", "MARKS_ENTRY", "PUBLISHED"), null, "status");
        if (to == null) throw bad("Choose the status.");
        if ("PUBLISHED".equals(to)) {
            if (papers.findByExamId(id).isEmpty()) throw bad("Add the date sheet first.");
            int missing = 0;
            for (RebSection sec : sections.findBySessionId(e.getSessionId())) {
                for (Map<String, Object> r : tabulate(e, sec.getId())) missing += (Integer) r.get("missing");
            }
            if (missing > 0) throw bad(missing + " marks are still missing. Enter them (or mark the girl absent) before publishing.");
            publish(e);
            return "Results published. Report cards can now be printed.";
        }
        if ("DRAFT".equals(to) && !marks.findByPaperIdIn(papers.findByExamId(id).stream().map(RebExamPaper::getId).toList()).isEmpty()) {
            throw bad("Marks have been entered; the exam can't go back to draft.");
        }
        if ("PUBLISHED".equals(e.getStatus())) {
            for (RebExamResult r : results.findByExamId(id)) {
                r.setPublished(false);
                results.save(r);
            }
            e.setPublishedAt(null);
        }
        e.setStatus(to);
        exams.save(e);
        return "DRAFT".equals(to) ? "The exam is a draft again." : "Marks entry is open.";
    }

    private void publish(RebExam e) {
        Map<Long, RebExamResult> existing = new HashMap<>();
        results.findByExamId(e.getId()).forEach(r -> existing.put(r.getStudentId(), r));
        for (RebSection sec : sections.findBySessionId(e.getSessionId())) {
            for (Map<String, Object> row : tabulate(e, sec.getId())) {
                Long sid = (Long) row.get("student_id");
                RebExamResult r = existing.computeIfAbsent(sid, k -> {
                    RebExamResult n = new RebExamResult();
                    n.setExamId(e.getId());
                    n.setStudentId(k);
                    return n;
                });
                r.setSectionId(sec.getId());
                r.setMadrasaTotal((BigDecimal) row.get("madrasa_total"));
                r.setMadrasaMax((BigDecimal) row.get("madrasa_max"));
                r.setSchoolTotal((BigDecimal) row.get("school_total"));
                r.setSchoolMax((BigDecimal) row.get("school_max"));
                r.setTotal((BigDecimal) row.get("total"));
                r.setMaxTotal((BigDecimal) row.get("max_total"));
                r.setPercent((BigDecimal) row.get("percent"));
                r.setGrade((String) row.get("grade"));
                r.setRankInSection((Integer) row.get("rank"));
                r.setResult((String) row.get("result"));
                r.setAttendancePercent((BigDecimal) row.get("attendance_percent"));
                r.setPublished(true);
                results.save(r);
            }
        }
        e.setStatus("PUBLISHED");
        e.setPublishedAt(LocalDateTime.now());
        exams.save(e);
    }

    // ---------------------------------------------------------------- date sheet

    /**
     * Every class of the session that has sections, with the subjects of its curriculum: included (has a paper),
     * date, maximum and pass marks.
     */
    public Map<String, Object> dateSheet(Long examId) {
        RebExam e = require(examId);
        Map<String, RebExamPaper> existing = new HashMap<>();
        papers.findByExamId(examId).forEach(p -> existing.put(p.getClassId() + ":" + p.getSubjectId(), p));
        Map<Long, RebSubject> subjectById = new HashMap<>();
        subjects.findAll().forEach(s -> subjectById.put(s.getId(), s));
        Set<Long> classesWithSections = new HashSet<>();
        sections.findBySessionId(e.getSessionId()).forEach(s -> classesWithSections.add(s.getClassId()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RebClass c : classes.findAllByOrderByLevelOrderAscNameAsc()) {
            if (!classesWithSections.contains(c.getId())) continue;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (RebClassSubject cs : classSubjects.findBySessionIdAndClassIdOrderByDisplayOrderAscIdAsc(e.getSessionId(), c.getId())) {
                RebExamPaper p = existing.get(c.getId() + ":" + cs.getSubjectId());
                RebSubject s = subjectById.get(cs.getSubjectId());
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("subject_id", cs.getSubjectId());
                r.put("subject_name", s == null ? null : s.getName());
                r.put("track", s == null ? null : s.getTrack());
                r.put("included", p != null);
                r.put("paper_id", p == null ? null : p.getId());
                r.put("exam_date", p == null ? null : p.getExamDate());
                r.put("max_marks", p == null ? cs.getMaxMarks() : p.getMaxMarks());
                r.put("pass_marks", p == null ? cs.getPassMarks() : p.getPassMarks());
                rows.add(r);
            }
            out.add(Map.of("class_id", c.getId(), "class_name", c.getName(), "subjects", rows));
        }
        return Map.of("exam", Rows.of(e), "classes", out);
    }

    /** Body: { papers: [{ classId, subjectId, included, examDate, maxMarks, passMarks }] }. Not after marks entry starts for removed papers with marks. */
    @Transactional
    public void saveDateSheet(Long examId, Map<String, Object> body) {
        RebExam e = require(examId);
        if ("PUBLISHED".equals(e.getStatus())) throw bad("The results are published; unpublish to change the date sheet.");
        if (!(body.get("papers") instanceof List<?> list)) throw bad("Send the date sheet.");
        Map<String, RebExamPaper> existing = new HashMap<>();
        papers.findByExamId(examId).forEach(p -> existing.put(p.getClassId() + ":" + p.getSubjectId(), p));
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid paper.");
            Long classId = id(m.get("classId")), subjectId = id(m.get("subjectId"));
            if (classId == null || subjectId == null) throw bad("Invalid paper.");
            RebExamPaper p = existing.get(classId + ":" + subjectId);
            if (!flag(m.get("included"), true)) {
                if (p != null) {
                    if (!marks.findByPaperId(p.getId()).isEmpty()) throw bad("Marks were entered for a paper being removed; it can't be removed.");
                    papers.delete(p);
                }
                continue;
            }
            if (p == null) {
                p = new RebExamPaper();
                p.setExamId(examId);
                p.setClassId(classId);
                p.setSubjectId(subjectId);
            }
            p.setExamDate(date(m.get("examDate"), "Exam date"));
            Integer max = number(m.get("maxMarks"), "Maximum marks", 1, 1000), pass = number(m.get("passMarks"), "Pass marks", 0, 1000);
            p.setMaxMarks(max == null ? 100 : max);
            p.setPassMarks(pass == null ? 0 : pass);
            if (p.getPassMarks() > p.getMaxMarks()) throw bad("Pass marks can't be more than the maximum marks.");
            papers.save(p);
        }
    }

    // ---------------------------------------------------------------- marks

    /** The papers the signed-in user may enter marks for (all papers, or a teacher's own subjects), with progress. */
    public List<Map<String, Object>> myPapers(Long examId) {
        RebExam e = require(examId);
        RebClassScope.Scope scope = classScope.current();
        Map<Long, String> classNames = new HashMap<>();
        classes.findAll().forEach(c -> classNames.put(c.getId(), c.getName()));
        Map<Long, RebSubject> subjectById = new HashMap<>();
        subjects.findAll().forEach(s -> subjectById.put(s.getId(), s));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebExamPaper p : papers.findByExamId(examId)) {
            List<RebSection> secs = sectionsFor(e, p, scope);
            if (secs.isEmpty()) continue;
            int girls = 0;
            for (RebSection s : secs) girls += (int) enrollments.findBySectionId(s.getId()).stream().filter(x -> "ACTIVE".equals(x.getStatus())).count();
            RebSubject s = subjectById.get(p.getSubjectId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("paper_id", p.getId());
            r.put("class_name", classNames.get(p.getClassId()));
            r.put("subject_name", s == null ? null : s.getName());
            r.put("track", s == null ? null : s.getTrack());
            r.put("exam_date", p.getExamDate());
            r.put("max_marks", p.getMaxMarks());
            r.put("girls", girls);
            r.put("entered", marks.findByPaperId(p.getId()).size());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("class_name"))).thenComparing(r -> String.valueOf(r.get("subject_name"))));
        return rows;
    }

    /** The sections of the paper's class in the exam's session that the user may enter this subject for. */
    private List<RebSection> sectionsFor(RebExam e, RebExamPaper p, RebClassScope.Scope scope) {
        return sections.findBySessionIdAndClassId(e.getSessionId(), p.getClassId()).stream()
                .filter(s -> scope.teaches(s.getId(), p.getSubjectId())).sorted(Comparator.comparing(RebSection::getName)).toList();
    }

    /** The marks sheet of a paper: the girls of the class (a teacher: of her sections) with their marks. */
    public Map<String, Object> marksSheet(Long paperId) {
        RebExamPaper p = papers.findById(paperId).orElseThrow(() -> notFound("Paper"));
        RebExam e = require(p.getExamId());
        List<RebSection> secs = sectionsFor(e, p, classScope.current());
        if (secs.isEmpty()) throw notFound("Paper");
        Map<Long, RebMark> byGirl = new HashMap<>();
        marks.findByPaperId(paperId).forEach(m -> byGirl.put(m.getStudentId(), m));
        Map<Long, String> labels = studentService.sectionLabels();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebSection s : secs) {
            for (RebEnrollment en : enrollments.findBySectionId(s.getId())) {
                if (!"ACTIVE".equals(en.getStatus())) continue;
                RebStudent g = students.findById(en.getStudentId()).orElse(null);
                if (g == null) continue;
                RebMark m = byGirl.get(g.getId());
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("student_id", g.getId());
                r.put("name", g.getName());
                r.put("roll_no", en.getRollNo());
                r.put("section", labels.get(s.getId()));
                r.put("marks", m == null ? null : m.getMarks());
                r.put("absent", m != null && m.isAbsent());
                r.put("remark", m == null ? null : m.getRemark());
                rows.add(r);
            }
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("section")))
                .thenComparing(r -> RebAttendanceService.rollNumber((String) r.get("roll_no")))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        RebSubject subj = subjects.findById(p.getSubjectId()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paper", Rows.of(p));
        out.put("exam", Rows.pick(e, "id", "name", "status"));
        out.put("subject_name", subj == null ? null : subj.getName());
        out.put("class_name", classes.findById(p.getClassId()).map(RebClass::getName).orElse(null));
        out.put("editable", "MARKS_ENTRY".equals(e.getStatus()));
        out.put("rows", rows);
        return out;
    }

    /** Body: { marks: [{ studentId, marks, absent, remark }] }. Only while the exam is open for marks entry. */
    @Transactional
    public String saveMarks(Long paperId, Map<String, Object> body) {
        RebExamPaper p = papers.findById(paperId).orElseThrow(() -> notFound("Paper"));
        RebExam e = require(p.getExamId());
        if (!"MARKS_ENTRY".equals(e.getStatus())) throw bad("Marks can only be entered while the exam is open for marks entry.");
        List<RebSection> secs = sectionsFor(e, p, classScope.current());
        if (secs.isEmpty()) throw notFound("Paper");
        Set<Long> allowed = new HashSet<>();
        secs.forEach(s -> enrollments.findBySectionId(s.getId()).forEach(en -> allowed.add(en.getStudentId())));
        if (!(body.get("marks") instanceof List<?> list)) throw bad("Send the marks.");
        Map<Long, RebMark> existing = new HashMap<>();
        marks.findByPaperId(paperId).forEach(m -> existing.put(m.getStudentId(), m));
        int saved = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid marks.");
            Long sid = id(m.get("studentId"));
            if (!allowed.contains(sid)) throw bad("A girl in the list is not in your class.");
            boolean absent = flag(m.get("absent"), false);
            BigDecimal value = absent ? null : money(m.get("marks"), "Marks");
            if (value != null && value.compareTo(BigDecimal.valueOf(p.getMaxMarks())) > 0) {
                throw bad("Marks can't be more than " + p.getMaxMarks() + ".");
            }
            RebMark mark = existing.get(sid);
            if (value == null && !absent) { // cleared
                if (mark != null) marks.delete(mark);
                continue;
            }
            if (mark == null) {
                mark = new RebMark();
                mark.setPaperId(paperId);
                mark.setStudentId(sid);
            }
            mark.setMarks(value);
            mark.setAbsent(absent);
            mark.setRemark(text(m.get("remark"), "Remark", 200, false));
            marks.save(mark);
            saved++;
        }
        return saved + " marks saved.";
    }

    // ---------------------------------------------------------------- results

    /**
     * The tabulation sheet of a section (computed from the marks): per girl her marks per paper, the Madrasa and
     * School totals, percentage, grade, PASS / FAIL / ABSENT, rank in the section, attendance during the session and
     * how many marks are missing.
     */
    List<Map<String, Object>> tabulate(RebExam e, Long sectionId) {
        RebSection sec = sections.findById(sectionId).orElseThrow(() -> notFound("Class"));
        List<RebExamPaper> ps = papers.findByExamIdAndClassId(e.getId(), sec.getClassId());
        Map<Long, RebSubject> subjectById = new HashMap<>();
        subjects.findAll().forEach(s -> subjectById.put(s.getId(), s));
        Map<String, RebMark> markOf = new HashMap<>();
        if (!ps.isEmpty()) marks.findByPaperIdIn(ps.stream().map(RebExamPaper::getId).toList()).forEach(m -> markOf.put(m.getPaperId() + ":" + m.getStudentId(), m));
        List<RebScaleBand> scale = bands(e.getSessionId());
        List<RebEnrollment> roll = enrollments.findBySectionId(sectionId).stream().filter(x -> !"LEFT".equals(x.getStatus())).toList();
        RebSession session = setup.session(e.getSessionId());
        Map<Long, BigDecimal> attendancePercent = attendance.percents(roll.stream().map(RebEnrollment::getStudentId).toList(),
                session.getStartDate(), e.getEndDate() != null ? e.getEndDate() : session.getEndDate());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebEnrollment en : roll) {
            RebStudent g = students.findById(en.getStudentId()).orElse(null);
            if (g == null) continue;
            BigDecimal mT = BigDecimal.ZERO, mMax = BigDecimal.ZERO, sT = BigDecimal.ZERO, sMax = BigDecimal.ZERO;
            int missing = 0, absent = 0;
            boolean failed = false;
            List<Map<String, Object>> cells = new ArrayList<>();
            for (RebExamPaper p : ps) {
                RebMark m = markOf.get(p.getId() + ":" + g.getId());
                RebSubject subj = subjectById.get(p.getSubjectId());
                boolean madrasa = subj != null && "MADRASA".equals(subj.getTrack());
                BigDecimal max = BigDecimal.valueOf(p.getMaxMarks());
                if (madrasa) mMax = mMax.add(max); else sMax = sMax.add(max);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("paper_id", p.getId());
                c.put("subject_name", subj == null ? null : subj.getName());
                c.put("track", madrasa ? "MADRASA" : "SCHOOL");
                c.put("max_marks", p.getMaxMarks());
                c.put("pass_marks", p.getPassMarks());
                if (m == null) {
                    missing++;
                    c.put("marks", null);
                } else if (m.isAbsent()) {
                    absent++;
                    failed = true;
                    c.put("absent", true);
                } else {
                    c.put("marks", m.getMarks());
                    if (madrasa) mT = mT.add(m.getMarks()); else sT = sT.add(m.getMarks());
                    if (m.getMarks().compareTo(BigDecimal.valueOf(p.getPassMarks())) < 0) failed = true;
                    c.put("passed", m.getMarks().compareTo(BigDecimal.valueOf(p.getPassMarks())) >= 0);
                }
                cells.add(c);
            }
            BigDecimal total = mT.add(sT), max = mMax.add(sMax);
            BigDecimal percent = max.signum() == 0 ? null : total.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP);
            RebScaleBand band = grade(scale, percent);
            String result = ps.isEmpty() ? null : absent == ps.size() ? "ABSENT" : missing > 0 ? null
                    : (failed || (band != null && !band.pass())) ? "FAIL" : "PASS";
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", g.getId());
            r.put("name", g.getName());
            r.put("admission_no", g.getAdmissionNo());
            r.put("roll_no", en.getRollNo());
            r.put("papers", cells);
            r.put("madrasa_total", mMax.signum() == 0 ? null : mT);
            r.put("madrasa_max", mMax.signum() == 0 ? null : mMax);
            r.put("school_total", sMax.signum() == 0 ? null : sT);
            r.put("school_max", sMax.signum() == 0 ? null : sMax);
            r.put("total", total);
            r.put("max_total", max);
            r.put("percent", percent);
            r.put("grade", band == null ? null : band.grade());
            r.put("result", result);
            r.put("missing", missing);
            r.put("attendance_percent", attendancePercent.get(g.getId()));
            rows.add(r);
        }
        // rank by percentage among girls who sat the exam (ties share a rank)
        List<Map<String, Object>> ranked = rows.stream().filter(r -> r.get("percent") != null && !"ABSENT".equals(r.get("result")))
                .sorted(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("percent")).reversed()).toList();
        int rank = 0;
        BigDecimal last = null;
        for (int i = 0; i < ranked.size(); i++) {
            BigDecimal p = (BigDecimal) ranked.get(i).get("percent");
            if (last == null || p.compareTo(last) != 0) rank = i + 1;
            ranked.get(i).put("rank", rank);
            last = p;
        }
        rows.forEach(r -> r.putIfAbsent("rank", null));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> RebAttendanceService.rollNumber((String) r.get("roll_no")))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        return rows;
    }

    /** The tabulation sheet of a section with the remarks; published exams show the kept results. */
    public Map<String, Object> results(Long examId, Long sectionId) {
        RebExam e = require(examId);
        classScope.requireSection(sectionId);
        List<Map<String, Object>> rows = tabulate(e, sectionId);
        Map<Long, RebExamResult> kept = new HashMap<>();
        results.findByExamId(examId).forEach(r -> kept.put(r.getStudentId(), r));
        for (Map<String, Object> r : rows) {
            RebExamResult k = kept.get((Long) r.get("student_id"));
            r.put("teacher_remark", k == null ? null : k.getTeacherRemark());
            if (k != null && k.isPublished()) {
                r.put("percent", k.getPercent());
                r.put("grade", k.getGrade());
                r.put("rank", k.getRankInSection());
                r.put("result", k.getResult());
            }
        }
        return Map.of("exam", Rows.of(e), "section", studentService.sectionLabels().get(sectionId), "rows", rows,
                "bands", bands(e.getSessionId()));
    }

    /** Body: { remarks: [{ studentId, remark }] }: the class teacher's remarks for the report cards. */
    @Transactional
    public void saveRemarks(Long examId, Map<String, Object> body) {
        RebExam e = require(examId);
        if (!(body.get("remarks") instanceof List<?> list)) throw bad("Send the remarks.");
        RebClassScope.Scope scope = classScope.current();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid remark.");
            Long sid = id(m.get("studentId"));
            RebEnrollment en = enrollments.findByStudentIdAndSessionId(sid, e.getSessionId()).orElseThrow(() -> bad("A girl is not in this session."));
            if (!scope.seesSection(en.getSectionId())) throw bad("A girl in the list is not in your class.");
            RebExamResult r = results.findByExamIdAndStudentId(examId, sid).orElseGet(() -> {
                RebExamResult n = new RebExamResult();
                n.setExamId(examId);
                n.setStudentId(sid);
                n.setSectionId(en.getSectionId());
                return n;
            });
            r.setTeacherRemark(text(m.get("remark"), "Remark", 500, false));
            results.save(r);
        }
    }

    /**
     * The annual result of a section: each published exam with a weight counts by its weight (over the exams the
     * girl sat), giving a percentage, a grade and a suggestion: promote when the grade passes.
     */
    public Map<String, Object> finalResults(Long sessionId, Long sectionId) {
        RebSession session = setup.session(sessionId);
        classScope.requireSection(sectionId);
        List<RebExam> weighted = exams.findBySessionIdOrderByStartDateAscIdAsc(session.getId()).stream()
                .filter(e -> "PUBLISHED".equals(e.getStatus()) && e.getWeightPercent().signum() > 0).toList();
        List<RebScaleBand> scale = bands(session.getId());
        Map<Long, Map<Long, RebExamResult>> byExam = new HashMap<>();
        for (RebExam e : weighted) {
            Map<Long, RebExamResult> m = new HashMap<>();
            results.findByExamId(e.getId()).forEach(r -> m.put(r.getStudentId(), r));
            byExam.put(e.getId(), m);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebEnrollment en : enrollments.findBySectionId(sectionId)) {
            if ("LEFT".equals(en.getStatus())) continue;
            RebStudent g = students.findById(en.getStudentId()).orElse(null);
            if (g == null) continue;
            BigDecimal sum = BigDecimal.ZERO, weights = BigDecimal.ZERO;
            List<Map<String, Object>> parts = new ArrayList<>();
            for (RebExam e : weighted) {
                RebExamResult r = byExam.get(e.getId()).get(g.getId());
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("exam", e.getName());
                part.put("weight", e.getWeightPercent());
                part.put("percent", r == null ? null : r.getPercent());
                parts.add(part);
                if (r != null && r.getPercent() != null) {
                    sum = sum.add(r.getPercent().multiply(e.getWeightPercent()));
                    weights = weights.add(e.getWeightPercent());
                }
            }
            BigDecimal percent = weights.signum() == 0 ? null : sum.divide(weights, 2, RoundingMode.HALF_UP);
            RebScaleBand band = grade(scale, percent);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_id", g.getId());
            row.put("name", g.getName());
            row.put("roll_no", en.getRollNo());
            row.put("exams", parts);
            row.put("percent", percent);
            row.put("grade", band == null ? null : band.grade());
            row.put("suggestion", band == null ? "No results yet" : band.pass() ? "Promote" : "Same class again");
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> RebAttendanceService.rollNumber((String) r.get("roll_no"))).thenComparing(r -> String.valueOf(r.get("name"))));
        return Map.of("session", Rows.of(session), "section", studentService.sectionLabels().get(sectionId),
                "exams", weighted.stream().map(e -> Map.of("name", e.getName(), "weight", e.getWeightPercent())).toList(), "rows", rows);
    }

    /** A girl's published results (for her page). */
    public List<Map<String, Object>> ofStudent(Long studentId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebExamResult r : results.findByStudentId(studentId)) {
            if (!r.isPublished()) continue;
            RebExam e = exams.findById(r.getExamId()).orElse(null);
            if (e == null) continue;
            Map<String, Object> row = Rows.of(r);
            row.put("exam_name", e.getName());
            row.put("session_id", e.getSessionId());
            rows.add(row);
        }
        return rows;
    }
}
