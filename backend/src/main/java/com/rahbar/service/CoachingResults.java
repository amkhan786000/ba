package com.rahbar.service;

import com.rahbar.entity.CoachingExam;
import com.rahbar.repository.CoachingExamMarkRepository;
import com.rahbar.repository.CoachingExamRepository;
import com.rahbar.repository.CoachingExamSubjectRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Coaching exam results per student: the exams of their center and class, and their % in each one they sat. */
@Component
public class CoachingResults {

    /** One exam of a student's class: their % (null: absent, no marks). */
    public record ExamResult(Long examId, LocalDate date, BigDecimal percent) {}

    private final CoachingExamRepository exams;
    private final CoachingExamSubjectRepository examSubjects;
    private final CoachingExamMarkRepository marks;

    public CoachingResults(CoachingExamRepository exams, CoachingExamSubjectRepository examSubjects, CoachingExamMarkRepository marks) {
        this.exams = exams;
        this.examSubjects = examSubjects;
        this.marks = marks;
    }

    /**
     * For each student (id -> center and class, admitted on): the exams of their center and class from their admission
     * on, newest first, with their % (null when they have no marks in it).
     */
    public Map<Long, List<ExamResult>> of(Map<Long, StudentClass> students) {
        Set<Long> centers = new HashSet<>();
        students.values().forEach(s -> centers.add(s.centerId()));
        List<CoachingExam> list = exams.findAllByOrderByExamDateDescIdDesc().stream().filter(e -> centers.contains(e.getRccCenterId())).toList();
        Map<Long, Map<Long, BigDecimal>> totals = new HashMap<>();
        Map<Long, BigDecimal> max = new HashMap<>();
        if (!list.isEmpty()) {
            examSubjects.findByExamIdIn(list.stream().map(CoachingExam::getId).toList()).forEach(s -> max.merge(s.getExamId(), s.getMaxMarks(), BigDecimal::add));
            for (CoachingExam e : list) {
                marks.findByExamId(e.getId()).forEach(m -> totals.computeIfAbsent(e.getId(), k -> new HashMap<>()).merge(m.getStudentId(), m.getMarks(), BigDecimal::add));
            }
        }
        Map<Long, List<ExamResult>> out = new HashMap<>();
        students.forEach((id, s) -> {
            List<ExamResult> mine = new ArrayList<>();
            for (CoachingExam e : list) {
                if (!e.getRccCenterId().equals(s.centerId()) || !e.getClassLevel().equals(s.classLevel())) continue;
                BigDecimal total = totals.getOrDefault(e.getId(), Map.of()).get(id);
                if (total == null && s.admittedOn() != null && e.getExamDate().isBefore(s.admittedOn())) continue; // before they joined
                BigDecimal m = max.getOrDefault(e.getId(), BigDecimal.ZERO);
                mine.add(new ExamResult(e.getId(), e.getExamDate(),
                        total == null || m.signum() == 0 ? null : total.multiply(BigDecimal.valueOf(100)).divide(m, 1, RoundingMode.HALF_UP)));
            }
            out.put(id, mine);
        });
        return out;
    }

    /** What {@link #of} needs to know about a student. */
    public record StudentClass(Long centerId, Integer classLevel, LocalDate admittedOn) {}

    /** Average % of the exams sat (one decimal), or null. */
    public static BigDecimal average(List<ExamResult> results) {
        List<BigDecimal> sat = results.stream().map(ExamResult::percent).filter(Objects::nonNull).toList();
        if (sat.isEmpty()) return null;
        return sat.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(sat.size()), 1, RoundingMode.HALF_UP);
    }
}
