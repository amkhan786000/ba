package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Coaching Center > Dashboard. The office sees all its centers together (with a row per center) or one center; a
 * coaching center coordinator or teacher always sees only their own center.
 */
@Service
public class CoachingDashboardService {

    static final int MONTHS = 12, RECENT_EXAMS = 8;

    private final CoachingStudentRepository students;
    private final CoachingTeacherRepository teachers;
    private final CoachingExamRepository exams;
    private final CoachingExamSubjectRepository examSubjects;
    private final CoachingExamMarkRepository marks;
    private final CoachingExamPrizeRepository prizes;
    private final CoachingAccess access;
    private final CoachingService coaching;
    private final CoachingAttendanceRepository attendance;
    private final CoachingResults coachingResults;
    private final CoachingPipelineService pipeline;

    public CoachingDashboardService(CoachingStudentRepository students, CoachingTeacherRepository teachers, CoachingExamRepository exams,
                                    CoachingExamSubjectRepository examSubjects, CoachingExamMarkRepository marks,
                                    CoachingExamPrizeRepository prizes, CoachingAccess access, CoachingService coaching,
                                    CoachingAttendanceRepository attendance, CoachingResults coachingResults, CoachingPipelineService pipeline) {
        this.attendance = attendance;
        this.coachingResults = coachingResults;
        this.pipeline = pipeline;
        this.students = students;
        this.teachers = teachers;
        this.exams = exams;
        this.examSubjects = examSubjects;
        this.marks = marks;
        this.prizes = prizes;
        this.access = access;
        this.coaching = coaching;
    }

    /** A student's result in one exam: total marks out of the exam's maximum. */
    record Result(Long examId, Long studentId, BigDecimal total, BigDecimal max) {
        BigDecimal percent() {
            return max.signum() == 0 ? BigDecimal.ZERO : total.multiply(BigDecimal.valueOf(100)).divide(max, 4, RoundingMode.HALF_UP);
        }
    }

    /** centerId: one center (must be the user's); null: all the user's centers (for a coordinator / teacher: theirs). */
    public Map<String, Object> dashboard(Long centerId) {
        Map<Long, String> centerNames = access.names();
        Set<Long> chosen;
        if (centerId != null) {
            access.requireVisible(centerId, "Coaching center");
            chosen = Set.of(centerId);
        } else {
            chosen = centerNames.keySet();
        }
        LocalDate today = LocalDate.now();

        List<CoachingStudent> studentList = students.findAll().stream().filter(s -> chosen.contains(s.getRccCenterId())).toList();
        List<CoachingStudent> active = studentList.stream().filter(s -> "ACTIVE".equals(s.getStatus())).toList();
        List<CoachingTeacher> teacherList = teachers.findAll().stream().filter(t -> chosen.contains(t.getRccCenterId()) && t.isActive()).toList();
        List<CoachingExam> examList = exams.findAllByOrderByExamDateDescIdDesc().stream().filter(e -> chosen.contains(e.getRccCenterId())).toList();
        Map<Long, List<Result>> results = results(examList);

        Map<String, Object> out = new LinkedHashMap<>(coaching.centers());
        out.put("center_id", centerId);
        out.put("center_name", centerId == null ? null : centerNames.get(centerId));

        // tiles
        Map<String, Object> tiles = new LinkedHashMap<>();
        tiles.put("students", active.size());
        tiles.put("girls", active.stream().filter(s -> "F".equals(s.getGender())).count());
        tiles.put("boys", active.stream().filter(s -> "M".equals(s.getGender())).count());
        tiles.put("left", studentList.size() - active.size());
        tiles.put("admitted_this_year", studentList.stream().filter(s -> s.getAdmissionDate() != null && s.getAdmissionDate().getYear() == today.getYear()).count());
        tiles.put("teachers", teacherList.size());
        tiles.put("exams_this_year", examList.stream().filter(e -> e.getExamDate().getYear() == today.getYear()).count());
        List<Result> recent = examList.stream().filter(e -> !e.getExamDate().isBefore(today.minusMonths(3)))
                .flatMap(e -> results.getOrDefault(e.getId(), List.of()).stream()).toList();
        tiles.put("average_percent_3_months", average(recent));
        List<CoachingAttendance> attendanceYear = attendance.findByAttDateBetween(today.minusMonths(MONTHS - 1).withDayOfMonth(1), today).stream()
                .filter(a -> chosen.contains(a.getRccCenterId())).toList();
        tiles.put("attendance_30_days", attendancePercent(attendanceYear.stream().filter(a -> !a.getAttDate().isBefore(today.minusDays(29))).toList()));
        out.put("tiles", tiles);

        // students by class (and gender)
        List<String> labels = CoachingService.CLASSES.stream().map(c -> "Class " + c).toList();
        out.put("by_class", Map.of("labels", labels, "series", List.of(
                Map.of("name", "Girls", "values", CoachingService.CLASSES.stream().map(c -> count(active, c, "F")).toList()),
                Map.of("name", "Boys", "values", CoachingService.CLASSES.stream().map(c -> count(active, c, "M")).toList()))));

        // last 12 months: average % per class, and admissions
        List<YearMonth> months = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) months.add(YearMonth.from(today).minusMonths(i));
        List<String> monthLabels = months.stream().map(m -> m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + (m.getYear() % 100)).toList();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Integer c : CoachingService.CLASSES) {
            List<Object> values = new ArrayList<>();
            for (YearMonth m : months) {
                List<Result> inMonth = examList.stream().filter(e -> e.getClassLevel().equals(c) && YearMonth.from(e.getExamDate()).equals(m))
                        .flatMap(e -> results.getOrDefault(e.getId(), List.of()).stream()).toList();
                values.add(average(inMonth));
            }
            if (values.stream().anyMatch(Objects::nonNull)) trend.add(Map.of("name", "Class " + c, "values", values));
        }
        out.put("average_trend", Map.of("labels", monthLabels, "series", trend));
        List<Object> attendanceByMonth = new ArrayList<>();
        for (YearMonth m : months) attendanceByMonth.add(attendancePercent(attendanceYear.stream().filter(a -> YearMonth.from(a.getAttDate()).equals(m)).toList()));
        out.put("attendance_trend", Map.of("labels", monthLabels, "series", List.of(Map.of("name", "Attendance %", "values", attendanceByMonth))));

        // how the studying students did in their latest exam
        Map<Long, CoachingResults.StudentClass> classes = new HashMap<>();
        active.forEach(st -> classes.put(st.getId(), new CoachingResults.StudentClass(st.getRccCenterId(), st.getClassLevel(), st.getAdmissionDate())));
        int[] bands = new int[4];
        coachingResults.of(classes).values().forEach(list -> list.stream().map(CoachingResults.ExamResult::percent).filter(Objects::nonNull).findFirst()
                .ifPresent(p -> bands[p.doubleValue() < 40 ? 0 : p.doubleValue() < 60 ? 1 : p.doubleValue() < 75 ? 2 : 3]++));
        out.put("marks_bands", List.of(Map.of("label", "75% and above", "value", bands[3]), Map.of("label", "60–74%", "value", bands[2]),
                Map.of("label", "40–59%", "value", bands[1]), Map.of("label", "Below 40%", "value", bands[0])));

        // Class 10 -> Rahbar scholarship
        List<CoachingStudent> ten = active.stream().filter(st -> CoachingPipelineService.SCHOLARSHIP_CLASS == st.getClassLevel()).toList();
        Map<Long, ScholarshipApplication> apps = pipeline.link(ten);
        List<Map<String, Object>> stages = ten.stream().map(st -> Map.<String, Object>of("stage", CoachingPipelineService.stage(st, apps.get(st.getId())))).toList();
        out.put("scholarship_funnel", CoachingPipelineService.funnel(stages));

        out.put("admissions", Map.of("labels", monthLabels, "series", List.of(Map.of("name", "Admissions", "values",
                months.stream().map(m -> studentList.stream().filter(s -> s.getAdmissionDate() != null && YearMonth.from(s.getAdmissionDate()).equals(m)).count()).toList()))));

        // recent exams with their prize winners
        Map<Long, String> names = new HashMap<>();
        studentList.forEach(s -> names.put(s.getId(), s.getName()));
        List<CoachingExam> latest = examList.stream().limit(RECENT_EXAMS).toList();
        Map<Long, List<CoachingExamPrize>> prizesOf = latest.isEmpty() ? Map.of()
                : prizes.findByExamIdIn(latest.stream().map(CoachingExam::getId).toList()).stream().collect(Collectors.groupingBy(CoachingExamPrize::getExamId));
        List<Map<String, Object>> recentRows = new ArrayList<>();
        for (CoachingExam e : latest) {
            List<Result> r = results.getOrDefault(e.getId(), List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("name", e.getName());
            row.put("exam_date", e.getExamDate());
            row.put("class_level", e.getClassLevel());
            row.put("center_name", centerNames.get(e.getRccCenterId()));
            row.put("appeared", r.size());
            row.put("average_percent", average(r));
            row.put("prizes", prizesOf.getOrDefault(e.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(CoachingExamPrize::getPosition))
                    .map(p -> Map.of("position", p.getPosition(), "name", names.getOrDefault(p.getStudentId(), "?"))).toList());
            recentRows.add(row);
        }
        out.put("recent_exams", recentRows);

        // all centers together: one row per center
        if (centerId == null && !access.scoped()) {
            List<Map<String, Object>> perCenter = new ArrayList<>();
            for (Map.Entry<Long, String> c : centerNames.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).toList()) {
                Long id = c.getKey();
                List<CoachingStudent> act = active.stream().filter(s -> id.equals(s.getRccCenterId())).toList();
                List<CoachingExam> ex = examList.stream().filter(e -> id.equals(e.getRccCenterId())).toList();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rcc_center_id", id);
                row.put("center_name", c.getValue());
                row.put("students", act.size());
                for (Integer cls : CoachingService.CLASSES) row.put("class_" + cls, act.stream().filter(s -> cls.equals(s.getClassLevel())).count());
                row.put("teachers", teacherList.stream().filter(t -> id.equals(t.getRccCenterId())).count());
                row.put("exams_this_year", ex.stream().filter(e -> e.getExamDate().getYear() == today.getYear()).count());
                row.put("last_exam", ex.isEmpty() ? null : ex.get(0).getExamDate());
                row.put("average_percent_3_months", average(ex.stream().filter(e -> !e.getExamDate().isBefore(today.minusMonths(3)))
                        .flatMap(e -> results.getOrDefault(e.getId(), List.of()).stream()).toList()));
                perCenter.add(row);
            }
            out.put("centers_summary", perCenter);
            out.put("students_by_center", perCenter.stream().map(r -> Map.of("label", r.get("center_name"), "value", r.get("students"))).toList());
        }
        return out;
    }

    /** Every student's total per exam (students with at least one mark), out of the exam's maximum. */
    private Map<Long, List<Result>> results(List<CoachingExam> list) {
        if (list.isEmpty()) return Map.of();
        List<Long> ids = list.stream().map(CoachingExam::getId).toList();
        Map<Long, BigDecimal> max = new HashMap<>();
        examSubjects.findByExamIdIn(ids).forEach(s -> max.merge(s.getExamId(), s.getMaxMarks(), BigDecimal::add));
        Map<Long, Map<Long, BigDecimal>> totals = new HashMap<>();
        for (Long id : ids) {
            marks.findByExamId(id).forEach(m -> totals.computeIfAbsent(id, k -> new HashMap<>()).merge(m.getStudentId(), m.getMarks(), BigDecimal::add));
        }
        Map<Long, List<Result>> out = new HashMap<>();
        totals.forEach((examId, byStudent) -> byStudent.forEach((studentId, total) ->
                out.computeIfAbsent(examId, k -> new ArrayList<>()).add(new Result(examId, studentId, total, max.getOrDefault(examId, BigDecimal.ZERO)))));
        return out;
    }

    /** Present / (present + absent) of these marks, or null. */
    private static BigDecimal attendancePercent(List<CoachingAttendance> list) {
        long present = list.stream().filter(a -> "P".equals(a.getStatus())).count();
        long absent = list.stream().filter(a -> "A".equals(a.getStatus())).count();
        return CoachingAttendanceService.percent(present, absent);
    }

    /** Average percentage (one decimal), or null when there are no results. */
    static BigDecimal average(List<Result> results) {
        if (results.isEmpty()) return null;
        BigDecimal sum = results.stream().map(Result::percent).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(results.size()), 1, RoundingMode.HALF_UP);
    }

    private static long count(List<CoachingStudent> list, Integer cls, String gender) {
        return list.stream().filter(s -> cls.equals(s.getClassLevel()) && gender.equals(s.getGender())).count();
    }
}
