package com.rahbar.service;

import com.rahbar.entity.CoachingAttendance;
import com.rahbar.entity.CoachingStudent;
import com.rahbar.repository.CoachingAttendanceRepository;
import com.rahbar.repository.CoachingStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Coaching Center > Attendance: the daily register of one class at one center (P present, A absent, L leave), and
 * each student's attendance over a period. Leave doesn't count against a student: % = present / (present + absent).
 */
@Service
public class CoachingAttendanceService {

    static final Set<String> STATUSES = Set.of("P", "A", "L");

    private final CoachingAttendanceRepository attendance;
    private final CoachingStudentRepository students;
    private final CoachingAccess access;
    private final CoachingService coaching;

    public CoachingAttendanceService(CoachingAttendanceRepository attendance, CoachingStudentRepository students,
                                     CoachingAccess access, CoachingService coaching) {
        this.attendance = attendance;
        this.students = students;
        this.access = access;
        this.coaching = coaching;
    }

    /** Present / (present + absent) as a percentage with one decimal; null when nothing counts. */
    static BigDecimal percent(long present, long absent) {
        long days = present + absent;
        return days == 0 ? null : BigDecimal.valueOf(present * 100).divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP);
    }

    /**
     * The register of a class on a day: its studying students (and anyone already marked) with their mark, and each
     * student's attendance over the 30 days up to that day.
     */
    public Map<String, Object> register(Long centerId, Integer classLevel, LocalDate date) {
        // no center chosen yet: the first of the user's centers
        Long center = centerId == null && !access.centers().isEmpty() ? access.centers().get(0).getRccCenterId() : access.requireCenter(centerId);
        // studying students per class at the center; no class chosen yet: the first class that has students
        Map<Integer, Long> perClass = new TreeMap<>();
        CoachingService.CLASSES.forEach(c -> perClass.put(c, 0L));
        students.findAll().stream().filter(s -> center.equals(s.getRccCenterId()) && "ACTIVE".equals(s.getStatus()))
                .forEach(s -> perClass.merge(s.getClassLevel(), 1L, Long::sum));
        Integer cls = classLevel != null ? CoachingService.classLevel(classLevel)
                : perClass.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).findFirst().orElse(CoachingService.CLASSES.get(0));
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<Long, String> marked = new HashMap<>();
        attendance.findByRccCenterIdAndClassLevelAndAttDate(center, cls, day).forEach(a -> marked.put(a.getStudentId(), a.getStatus()));
        Map<Long, CoachingStudent> who = new LinkedHashMap<>();
        students.findByRccCenterIdAndClassLevelAndStatusOrderByNameAsc(center, cls, "ACTIVE").forEach(s -> who.put(s.getId(), s));
        students.findAllById(marked.keySet()).forEach(s -> who.putIfAbsent(s.getId(), s));
        Map<Long, long[]> last30 = counts(who.keySet(), day.minusDays(29), day);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingStudent s : who.values()) {
            long[] c = last30.getOrDefault(s.getId(), new long[3]);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", s.getId());
            r.put("name", s.getName());
            r.put("admission_no", s.getAdmissionNo());
            r.put("status", marked.get(s.getId()));
            r.put("percent_30_days", percent(c[0], c[1]));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>(coaching.centers());
        out.put("rcc_center_id", center);
        out.put("class_level", cls);
        out.put("date", day);
        out.put("marked", !marked.isEmpty());
        out.put("students_per_class", perClass);
        out.put("rows", rows);
        return out;
    }

    /** Saves a day's register: { centerId, classLevel, date, entries: [{ studentId, status }] }; an empty status clears the mark. */
    @Transactional
    public Map<String, Object> save(Map<String, Object> b) {
        Long center = access.requireCenter(b.get("centerId"));
        Integer cls = CoachingService.classLevel(b.get("classLevel"));
        LocalDate day = Optional.ofNullable(CoachingService.date(b.get("date"), "Date")).orElseThrow(() -> CoachingService.bad("Date is required."));
        if (day.isAfter(LocalDate.now())) throw CoachingService.bad("Attendance can't be marked for a future day.");
        Map<Long, CoachingAttendance> existing = new HashMap<>();
        attendance.findByRccCenterIdAndClassLevelAndAttDate(center, cls, day).forEach(a -> existing.put(a.getStudentId(), a));
        int saved = 0;
        if (b.get("entries") instanceof Collection<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Long studentId = ServiceSupport.toLong(m.get("studentId"));
                CoachingStudent s = coaching.requireStudent(studentId);
                if (!s.getRccCenterId().equals(center) || !s.getClassLevel().equals(cls)) {
                    throw CoachingService.bad(s.getName() + " is not in class " + cls + " at this center.");
                }
                String status = CoachingService.blank(m.get("status")) ? null : String.valueOf(m.get("status")).trim().toUpperCase(Locale.ROOT);
                if (status != null && !STATUSES.contains(status)) throw CoachingService.bad("Attendance must be Present, Absent or Leave.");
                CoachingAttendance a = existing.get(studentId);
                if (status == null) {
                    if (a != null) { attendance.delete(a); saved++; }
                    continue;
                }
                if (a == null) {
                    a = new CoachingAttendance();
                    a.setRccCenterId(center);
                    a.setClassLevel(cls);
                    a.setAttDate(day);
                    a.setStudentId(studentId);
                } else if (status.equals(a.getStatus())) {
                    continue;
                }
                a.setStatus(status);
                attendance.save(a);
                saved++;
            }
        }
        return Map.of("saved", saved, "message", saved == 0 ? "No changes." : "Attendance saved.");
    }

    /** Each student's attendance between two days (the user's centers; optionally one center / class), lowest first. */
    public Map<String, Object> summary(Long centerId, Integer classLevel, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw CoachingService.bad("The start date is after the end date.");
        Set<Long> allowed = access.centerIds();
        if (centerId != null) access.requireVisible(centerId, "Coaching center");
        Map<Long, String> centerNames = access.names();
        List<CoachingStudent> list = students.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) && allowed.contains(s.getRccCenterId()))
                .filter(s -> centerId == null || centerId.equals(s.getRccCenterId()))
                .filter(s -> classLevel == null || classLevel.equals(s.getClassLevel()))
                .toList();
        Map<Long, long[]> counts = counts(list.stream().map(CoachingStudent::getId).toList(), start, end);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingStudent s : list) {
            long[] c = counts.getOrDefault(s.getId(), new long[3]);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", s.getId());
            r.put("name", s.getName());
            r.put("class_level", s.getClassLevel());
            r.put("center_name", centerNames.get(s.getRccCenterId()));
            r.put("present", c[0]);
            r.put("absent", c[1]);
            r.put("leave", c[2]);
            r.put("percent", percent(c[0], c[1]));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("percent"), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", start);
        out.put("to", end);
        out.put("rows", rows);
        return out;
    }

    /** Present / absent / leave days per student between two days. */
    Map<Long, long[]> counts(Collection<Long> studentIds, LocalDate from, LocalDate to) {
        Map<Long, long[]> out = new HashMap<>();
        if (studentIds.isEmpty()) return out;
        for (CoachingAttendance a : attendance.findByStudentIdInAndAttDateBetween(studentIds, from, to)) {
            long[] c = out.computeIfAbsent(a.getStudentId(), k -> new long[3]);
            switch (a.getStatus()) {
                case "P" -> c[0]++;
                case "A" -> c[1]++;
                default -> c[2]++;
            }
        }
        return out;
    }
}
