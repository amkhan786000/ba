package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Coaching Center > Exams: a (monthly) exam of one class at one center, with its subjects and their maximum marks;
 * marks per student per subject; and the 1st / 2nd / 3rd prizes from the totals. Equal totals share a position, and
 * the next different total takes the next position (two 1sts, then a 2nd).
 */
@Service
public class CoachingExamService {

    static final int PRIZES = 3;

    private final CoachingExamRepository exams;
    private final CoachingExamSubjectRepository examSubjects;
    private final CoachingExamMarkRepository marks;
    private final CoachingExamPrizeRepository prizes;
    private final CoachingSubjectRepository subjects;
    private final CoachingStudentRepository students;
    private final CoachingAccess access;
    private final CoachingService coaching;

    public CoachingExamService(CoachingExamRepository exams, CoachingExamSubjectRepository examSubjects, CoachingExamMarkRepository marks,
                               CoachingExamPrizeRepository prizes, CoachingSubjectRepository subjects, CoachingStudentRepository students,
                               CoachingAccess access, CoachingService coaching) {
        this.exams = exams;
        this.examSubjects = examSubjects;
        this.marks = marks;
        this.prizes = prizes;
        this.subjects = subjects;
        this.students = students;
        this.access = access;
        this.coaching = coaching;
    }

    /** The exams the user may see (optionally of one center / class), newest first, each with its subjects and prizes. */
    public Map<String, Object> list(Long centerId, Integer classLevel) {
        Set<Long> allowed = access.centerIds();
        Map<Long, String> centerNames = access.names();
        List<CoachingExam> list = exams.findAllByOrderByExamDateDescIdDesc().stream()
                .filter(e -> allowed.contains(e.getRccCenterId()))
                .filter(e -> centerId == null || centerId.equals(e.getRccCenterId()))
                .filter(e -> classLevel == null || classLevel.equals(e.getClassLevel()))
                .toList();
        List<Long> ids = list.stream().map(CoachingExam::getId).toList();
        Map<Long, String> subjectNames = subjectNames();
        Map<Long, List<Map<String, Object>>> subjectsOf = new HashMap<>();
        for (CoachingExamSubject s : ids.isEmpty() ? List.<CoachingExamSubject>of() : examSubjects.findByExamIdIn(ids)) {
            subjectsOf.computeIfAbsent(s.getExamId(), k -> new ArrayList<>())
                    .add(Map.of("subject_id", s.getSubjectId(), "name", subjectNames.getOrDefault(s.getSubjectId(), "?"), "max_marks", s.getMaxMarks()));
        }
        Map<Long, String> studentNames = new HashMap<>();
        students.findAll().forEach(s -> studentNames.put(s.getId(), s.getName()));
        Map<Long, List<Map<String, Object>>> prizesOf = new HashMap<>();
        for (CoachingExamPrize p : ids.isEmpty() ? List.<CoachingExamPrize>of() : prizes.findByExamIdIn(ids)) {
            prizesOf.computeIfAbsent(p.getExamId(), k -> new ArrayList<>())
                    .add(Map.of("position", p.getPosition(), "name", studentNames.getOrDefault(p.getStudentId(), "?"), "total", p.getTotal()));
        }
        prizesOf.values().forEach(l -> l.sort(Comparator.comparing(m -> (Integer) m.get("position"))));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingExam e : list) {
            Map<String, Object> r = Rows.of(e);
            r.put("center_name", centerNames.get(e.getRccCenterId()));
            r.put("subjects", subjectsOf.getOrDefault(e.getId(), List.of()));
            r.put("prizes", prizesOf.getOrDefault(e.getId(), List.of()));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>(coaching.centers());
        out.put("rows", rows);
        out.put("subjects", Rows.list(subjects.findAllByOrderBySortOrderAscNameAsc().stream().filter(CoachingSubject::isActive).toList()));
        return out;
    }

    /**
     * Adds or changes an exam: { rccCenterId, classLevel, name, examDate, subjects: [{ subjectId, maxMarks }] }. Once
     * marks are entered the center and class stay; a subject taken off loses its marks.
     */
    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> b) {
        CoachingExam e = id == null ? new CoachingExam() : requireExam(id);
        Long center = access.requireCenter(b.get("rccCenterId"));
        Integer cls = CoachingService.classLevel(b.get("classLevel"));
        boolean hasMarks = id != null && !marks.findByExamId(id).isEmpty();
        if (hasMarks && (!center.equals(e.getRccCenterId()) || !cls.equals(e.getClassLevel()))) {
            throw CoachingService.bad("Marks are already entered, so the center and class can't change.");
        }
        e.setRccCenterId(center);
        e.setClassLevel(cls);
        e.setName(CoachingService.text(b.get("name"), "Exam name", 100, true));
        e.setExamDate(Optional.ofNullable(CoachingService.date(b.get("examDate"), "Exam date")).orElseThrow(() -> CoachingService.bad("Exam date is required.")));

        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        Set<Long> known = subjectNames().keySet();
        if (b.get("subjects") instanceof Collection<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Long sid = ServiceSupport.toLong(m.get("subjectId"));
                if (sid == null || !known.contains(sid)) throw CoachingService.bad("Choose the subjects from the list.");
                BigDecimal max = CoachingService.number(m.get("maxMarks"), "Maximum marks", new BigDecimal("1000"));
                if (max == null || max.signum() == 0) throw CoachingService.bad("Every subject needs its maximum marks.");
                if (wanted.put(sid, max) != null) throw CoachingService.bad("A subject is listed twice.");
            }
        }
        if (wanted.isEmpty()) throw CoachingService.bad("Add at least one subject with its maximum marks.");
        exams.save(e);

        List<CoachingExamSubject> current = examSubjects.findByExamIdOrderByIdAsc(e.getId());
        Set<Long> removed = new HashSet<>();
        for (CoachingExamSubject s : current) {
            BigDecimal max = wanted.remove(s.getSubjectId());
            if (max == null) {
                removed.add(s.getSubjectId());
                examSubjects.delete(s);
            } else if (max.compareTo(s.getMaxMarks()) != 0) {
                for (CoachingExamMark m : marks.findByExamId(e.getId())) {
                    if (m.getSubjectId().equals(s.getSubjectId()) && m.getMarks().compareTo(max) > 0) {
                        throw CoachingService.bad("Some marks are above the new maximum of " + max.stripTrailingZeros().toPlainString() + ".");
                    }
                }
                s.setMaxMarks(max);
                examSubjects.save(s);
            }
        }
        wanted.forEach((sid, max) -> {
            CoachingExamSubject s = new CoachingExamSubject();
            s.setExamId(e.getId());
            s.setSubjectId(sid);
            s.setMaxMarks(max);
            examSubjects.save(s);
        });
        if (!removed.isEmpty()) {
            marks.findByExamId(e.getId()).stream().filter(m -> removed.contains(m.getSubjectId())).forEach(marks::delete);
            clearPrizes(e);
        }
        return Map.of("id", e.getId(), "message", "Exam saved.");
    }

    @Transactional
    public void delete(Long id) {
        CoachingExam e = requireExam(id);
        prizes.deleteByExamId(id);
        marks.deleteByExamId(id);
        examSubjects.deleteByExamId(id);
        exams.delete(e);
    }

    /**
     * The marks sheet: the exam, its subjects, and the class's students at that center (active ones, plus anyone who
     * has marks) with their marks, total and percentage, best first.
     */
    public Map<String, Object> sheet(Long id) {
        CoachingExam e = requireExam(id);
        List<CoachingExamSubject> subs = examSubjects.findByExamIdOrderByIdAsc(id);
        Map<Long, String> names = subjectNames();
        BigDecimal maxTotal = subs.stream().map(CoachingExamSubject::getMaxMarks).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, Map<Long, BigDecimal>> byStudent = new HashMap<>();
        marks.findByExamId(id).forEach(m -> byStudent.computeIfAbsent(m.getStudentId(), k -> new HashMap<>()).put(m.getSubjectId(), m.getMarks()));
        Map<Long, Integer> positions = new HashMap<>();
        prizes.findByExamIdOrderByPositionAscTotalDesc(id).forEach(p -> positions.put(p.getStudentId(), p.getPosition()));

        Map<Long, CoachingStudent> who = new LinkedHashMap<>();
        students.findByRccCenterIdAndClassLevelAndStatusOrderByNameAsc(e.getRccCenterId(), e.getClassLevel(), "ACTIVE").forEach(s -> who.put(s.getId(), s));
        students.findAllById(byStudent.keySet()).forEach(s -> who.putIfAbsent(s.getId(), s));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingStudent s : who.values()) {
            Map<Long, BigDecimal> m = byStudent.getOrDefault(s.getId(), Map.of());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", s.getId());
            r.put("name", s.getName());
            r.put("admission_no", s.getAdmissionNo());
            r.put("status", s.getStatus());
            Map<String, Object> perSubject = new LinkedHashMap<>();
            m.forEach((sid, v) -> perSubject.put(String.valueOf(sid), v));
            r.put("marks", perSubject);
            BigDecimal total = m.isEmpty() ? null : m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            r.put("total", total);
            r.put("percent", total == null || maxTotal.signum() == 0 ? null : total.multiply(BigDecimal.valueOf(100)).divide(maxTotal, 1, RoundingMode.HALF_UP));
            r.put("prize", positions.get(s.getId()));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("total"), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));

        Map<String, Object> exam = Rows.of(e);
        exam.put("center_name", access.names().get(e.getRccCenterId()));
        List<Map<String, Object>> subjectRows = new ArrayList<>();
        subs.forEach(s -> subjectRows.add(Map.of("subject_id", s.getSubjectId(), "name", names.getOrDefault(s.getSubjectId(), "?"), "max_marks", s.getMaxMarks())));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exam", exam);
        out.put("subjects", subjectRows);
        out.put("max_total", maxTotal);
        out.put("rows", rows);
        return out;
    }

    /**
     * Saves marks: { marks: [{ studentId, subjectId, marks }] }; empty marks removes them (absent / not entered).
     * Changed marks clear the prizes, which are then generated again.
     */
    @Transactional
    public Map<String, Object> saveMarks(Long id, Map<String, Object> b) {
        CoachingExam e = requireExam(id);
        Map<Long, BigDecimal> max = new HashMap<>();
        examSubjects.findByExamIdOrderByIdAsc(id).forEach(s -> max.put(s.getSubjectId(), s.getMaxMarks()));
        Map<String, CoachingExamMark> existing = new HashMap<>();
        marks.findByExamId(id).forEach(m -> existing.put(m.getStudentId() + ":" + m.getSubjectId(), m));
        Set<Long> checkedStudents = new HashSet<>();
        int changed = 0;
        if (b.get("marks") instanceof Collection<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Long studentId = ServiceSupport.toLong(m.get("studentId"));
                Long subjectId = ServiceSupport.toLong(m.get("subjectId"));
                if (studentId == null || subjectId == null || !max.containsKey(subjectId)) throw CoachingService.bad("Unknown student or subject.");
                if (checkedStudents.add(studentId)) {
                    CoachingStudent s = coaching.requireStudent(studentId);
                    if (!s.getRccCenterId().equals(e.getRccCenterId()) || !s.getClassLevel().equals(e.getClassLevel())) {
                        throw CoachingService.bad(s.getName() + " is not in this exam's center and class.");
                    }
                }
                BigDecimal value = CoachingService.number(m.get("marks"), "Marks", max.get(subjectId));
                CoachingExamMark row = existing.get(studentId + ":" + subjectId);
                if (value == null) {
                    if (row != null) { marks.delete(row); changed++; }
                } else if (row == null || row.getMarks().compareTo(value) != 0) {
                    if (row == null) {
                        row = new CoachingExamMark();
                        row.setExamId(id);
                        row.setStudentId(studentId);
                        row.setSubjectId(subjectId);
                    }
                    row.setMarks(value);
                    marks.save(row);
                    changed++;
                }
            }
        }
        boolean hadPrizes = e.getPrizesGeneratedAt() != null;
        if (changed > 0) clearPrizes(e);
        return Map.of("changed", changed, "message", changed == 0 ? "No changes." : "Marks saved."
                + (changed > 0 && hadPrizes ? " The prizes were cleared; generate them again." : ""));
    }

    /** The 1st, 2nd and 3rd prizes from the totals of the students who have marks; replaces earlier ones. */
    @Transactional
    public Map<String, Object> generatePrizes(Long id) {
        CoachingExam e = requireExam(id);
        Map<Long, BigDecimal> totals = new HashMap<>();
        marks.findByExamId(id).forEach(m -> totals.merge(m.getStudentId(), m.getMarks(), BigDecimal::add));
        if (totals.isEmpty()) throw CoachingService.bad("Enter the marks first.");
        prizes.deleteByExamId(id);
        prizes.flush();
        List<CoachingExamPrize> out = new ArrayList<>();
        for (Map.Entry<Long, Integer> w : positions(totals).entrySet()) {
            CoachingExamPrize p = new CoachingExamPrize();
            p.setExamId(id);
            p.setStudentId(w.getKey());
            p.setPosition(w.getValue());
            p.setTotal(totals.get(w.getKey()));
            out.add(prizes.save(p));
        }
        e.setPrizesGeneratedAt(LocalDateTime.now());
        exams.save(e);
        return sheet(id);
    }

    /**
     * Students in the top three different totals and their position (1, 2, 3); equal totals share a position.
     * E.g. totals 95, 95, 90, 88, 80 give 1, 1, 2, 3 and nothing for 80.
     */
    static Map<Long, Integer> positions(Map<Long, BigDecimal> totals) {
        List<BigDecimal> distinct = totals.values().stream().map(BigDecimal::stripTrailingZeros).distinct()
                .sorted(Comparator.reverseOrder()).limit(PRIZES).toList();
        Map<Long, Integer> out = new LinkedHashMap<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .forEach(t -> {
                    int i = distinct.indexOf(t.getValue().stripTrailingZeros());
                    if (i >= 0) out.put(t.getKey(), i + 1);
                });
        return out;
    }

    private void clearPrizes(CoachingExam e) {
        prizes.deleteByExamId(e.getId());
        e.setPrizesGeneratedAt(null);
        exams.save(e);
    }

    CoachingExam requireExam(Long id) {
        CoachingExam e = exams.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exam not found."));
        access.requireVisible(e.getRccCenterId(), "Exam");
        return e;
    }

    private Map<Long, String> subjectNames() {
        Map<Long, String> names = new HashMap<>();
        subjects.findAll().forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }
}
