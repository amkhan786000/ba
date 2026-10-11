package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.RebClassScope;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB attendance: one mark per girl per day (present, absent, late, leave), marked for a class at a time; the
 * monthly register; percentages (present + late out of the days marked, leave left out); and the holiday calendar
 * of a session. Teachers with the "own REB classes" scope only see and mark their own classes.
 */
@Service
public class RebAttendanceService {

    static final Set<String> STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "LEAVE");
    static final Set<String> KINDS = Set.of("HOLIDAY", "EVENT", "EXAM");

    private final RebAttendanceRepository attendance;
    private final RebCalendarEventRepository calendar;
    private final RebEnrollmentRepository enrollments;
    private final RebSectionRepository sections;
    private final RebStudentRepository students;
    private final RebSessionRepository sessions;
    private final RebSetupService setup;
    private final RebStudentService studentService;
    private final RebClassScope classScope;

    public RebAttendanceService(RebAttendanceRepository attendance, RebCalendarEventRepository calendar, RebEnrollmentRepository enrollments,
                                RebSectionRepository sections, RebStudentRepository students, RebSessionRepository sessions,
                                RebSetupService setup, RebStudentService studentService, RebClassScope classScope) {
        this.attendance = attendance;
        this.calendar = calendar;
        this.enrollments = enrollments;
        this.sections = sections;
        this.students = students;
        this.sessions = sessions;
        this.setup = setup;
        this.studentService = studentService;
        this.classScope = classScope;
    }

    private RebSection section(Long sectionId) {
        RebSection s = sectionId == null ? null : sections.findById(sectionId).orElse(null);
        if (s == null) throw notFound("Class");
        classScope.requireSection(sectionId);
        return s;
    }

    /** Girls studying in the section (active enrollment), by roll number then name. */
    private List<Map.Entry<RebEnrollment, RebStudent>> roll(RebSection section) {
        List<Map.Entry<RebEnrollment, RebStudent>> out = new ArrayList<>();
        for (RebEnrollment e : enrollments.findBySectionId(section.getId())) {
            if (!"ACTIVE".equals(e.getStatus())) continue;
            students.findById(e.getStudentId()).filter(s -> "ACTIVE".equals(s.getStatus())).ifPresent(s -> out.add(Map.entry(e, s)));
        }
        out.sort(Comparator.comparing((Map.Entry<RebEnrollment, RebStudent> x) -> rollNumber(x.getKey().getRollNo()))
                .thenComparing(x -> x.getValue().getName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    static int rollNumber(String r) {
        try {
            return r == null ? Integer.MAX_VALUE : Integer.parseInt(r.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The holiday or event on that day, if any. */
    private Optional<RebCalendarEvent> holiday(Long sessionId, LocalDate day) {
        return calendar.findBySessionIdOrderByDateFromAsc(sessionId).stream()
                .filter(c -> "HOLIDAY".equals(c.getKind()) && !day.isBefore(c.getDateFrom()) && !day.isAfter(c.getDateTo())).findFirst();
    }

    // ---------------------------------------------------------------- a day

    /** The class list for a day: each girl's mark (PRESENT when not marked yet), and whether the day was marked. */
    public Map<String, Object> day(Long sectionId, LocalDate date) {
        RebSection section = section(sectionId);
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<Long, RebAttendance> marked = new HashMap<>();
        attendance.findBySectionIdAndDate(section.getId(), day).forEach(a -> marked.put(a.getStudentId(), a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var x : roll(section)) {
            RebAttendance a = marked.get(x.getValue().getId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", x.getValue().getId());
            r.put("name", x.getValue().getName());
            r.put("roll_no", x.getKey().getRollNo());
            r.put("status", a == null ? "PRESENT" : a.getStatus());
            r.put("remark", a == null ? null : a.getRemark());
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("section_id", section.getId());
        out.put("section", studentService.sectionLabels().get(section.getId()));
        out.put("date", day);
        out.put("marked", !marked.isEmpty());
        out.put("holiday", holiday(section.getSessionId(), day).map(RebCalendarEvent::getTitle).orElse(null));
        out.put("rows", rows);
        return out;
    }

    /** Body: { sectionId, date, marks: [{ studentId, status, remark }] }. Saving again replaces the day's marks. */
    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        RebSection section = section(id(body.get("sectionId")));
        LocalDate day = date(body.get("date"), "Date");
        if (day == null) throw bad("Choose the day.");
        if (day.isAfter(LocalDate.now())) throw bad("Attendance can't be marked for a future day.");
        RebSession session = sessions.findById(section.getSessionId()).orElseThrow(() -> notFound("Session"));
        if (day.isBefore(session.getStartDate()) || day.isAfter(session.getEndDate())) throw bad("That day is outside the session " + session.getName() + ".");
        if (!(body.get("marks") instanceof List<?> list)) throw bad("Send the marks.");
        Set<Long> inClass = new HashSet<>();
        roll(section).forEach(x -> inClass.add(x.getValue().getId()));
        int present = 0, absent = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid mark.");
            Long studentId = id(m.get("studentId"));
            if (!inClass.contains(studentId)) throw bad("A girl in the list is not in this class.");
            String status = code(m.get("status"), STATUSES, "PRESENT", "attendance");
            RebAttendance a = attendance.findByStudentIdAndDate(studentId, day).orElseGet(RebAttendance::new);
            a.setStudentId(studentId);
            a.setSectionId(section.getId());
            a.setDate(day);
            a.setStatus(status);
            a.setRemark(text(m.get("remark"), "Remark", 200, false));
            attendance.save(a);
            if ("ABSENT".equals(status)) absent++; else if (!"LEAVE".equals(status)) present++;
        }
        return Map.of("message", "Attendance saved: " + present + " present, " + absent + " absent.");
    }

    // ---------------------------------------------------------------- register and percentages

    /** The month's register of a class: one row per girl, one letter per day (P, A, L, V for leave, H for holiday). */
    public Map<String, Object> register(Long sectionId, String month) {
        RebSection section = section(sectionId);
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(RebFeeService.period(month, "Month"));
        LocalDate first = ym.atDay(1), last = ym.atEndOfMonth();
        Map<String, String> marks = new HashMap<>();
        attendance.findBySectionIdAndDateBetween(section.getId(), first, last)
                .forEach(a -> marks.put(a.getStudentId() + ":" + a.getDate(), a.getStatus()));
        Set<LocalDate> holidays = new HashSet<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) if (holiday(section.getSessionId(), d).isPresent()) holidays.add(d);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var x : roll(section)) {
            Long sid = x.getValue().getId();
            StringBuilder days = new StringBuilder();
            int counted = 0, present = 0;
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                String st = marks.get(sid + ":" + d);
                char c = st == null ? (holidays.contains(d) ? 'H' : '.') : switch (st) {
                    case "PRESENT" -> 'P'; case "ABSENT" -> 'A'; case "LATE" -> 'L'; default -> 'V';
                };
                days.append(c);
                if (c == 'P' || c == 'L') { present++; counted++; } else if (c == 'A') counted++;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", sid);
            r.put("name", x.getValue().getName());
            r.put("roll_no", x.getKey().getRollNo());
            r.put("days", days.toString());
            r.put("present", present);
            r.put("percent", percent(present, counted));
            rows.add(r);
        }
        return Map.of("section", studentService.sectionLabels().get(section.getId()), "month", ym.toString(),
                "days_in_month", ym.lengthOfMonth(), "rows", rows);
    }

    static BigDecimal percent(int present, int counted) {
        return counted == 0 ? null : BigDecimal.valueOf(present * 100.0 / counted).setScale(1, RoundingMode.HALF_UP);
    }

    /** A girl's attendance in a session (default: current): counts by status and the percentage. */
    public Map<String, Object> ofStudent(Long studentId, Long sessionId) {
        studentService.require(studentId);
        RebSession session = setup.session(sessionId);
        return summary(studentId, session.getStartDate(), session.getEndDate());
    }

    /** Counts and percentage between two days. */
    public Map<String, Object> summary(Long studentId, LocalDate from, LocalDate to) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        STATUSES.stream().sorted().forEach(s -> counts.put(s, 0));
        for (RebAttendance a : attendance.findByStudentIdAndDateBetween(studentId, from, to)) counts.merge(a.getStatus(), 1, Integer::sum);
        int present = counts.get("PRESENT") + counts.get("LATE");
        Map<String, Object> out = new LinkedHashMap<>(counts);
        out.put("percent", percent(present, present + counts.get("ABSENT")));
        return out;
    }

    /** Attendance percentage of many girls between two days (for results and reports). */
    public Map<Long, BigDecimal> percents(Collection<Long> studentIds, LocalDate from, LocalDate to) {
        Map<Long, int[]> c = new HashMap<>();
        if (!studentIds.isEmpty()) {
            for (RebAttendance a : attendance.findByStudentIdInAndDateBetween(studentIds, from, to)) {
                int[] x = c.computeIfAbsent(a.getStudentId(), k -> new int[2]);
                if ("PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus())) { x[0]++; x[1]++; }
                else if ("ABSENT".equals(a.getStatus())) x[1]++;
            }
        }
        Map<Long, BigDecimal> out = new HashMap<>();
        c.forEach((k, v) -> out.put(k, percent(v[0], v[1])));
        return out;
    }

    // ---------------------------------------------------------------- calendar

    public List<Map<String, Object>> calendar(Long sessionId) {
        return Rows.list(calendar.findBySessionIdOrderByDateFromAsc(setup.session(sessionId).getId()));
    }

    /** Body: { sessionId, dateFrom, dateTo (default dateFrom), title, kind }. */
    @Transactional
    public RebCalendarEvent saveEvent(Long id, Map<String, Object> body) {
        RebCalendarEvent c = id == null ? new RebCalendarEvent() : calendar.findById(id).orElseThrow(() -> notFound("Calendar entry"));
        RebSession session = setup.session(id(body.get("sessionId")) != null ? id(body.get("sessionId")) : c.getSessionId());
        c.setSessionId(session.getId());
        c.setDateFrom(date(body.get("dateFrom"), "From"));
        if (c.getDateFrom() == null) throw bad("Choose the day.");
        LocalDate to = date(body.get("dateTo"), "To");
        c.setDateTo(to == null ? c.getDateFrom() : to);
        if (c.getDateTo().isBefore(c.getDateFrom())) throw bad("The last day is before the first day.");
        c.setTitle(text(body.get("title"), "Title", 150, true));
        c.setKind(code(body.get("kind"), KINDS, "HOLIDAY", "kind"));
        return calendar.save(c);
    }

    @Transactional
    public void deleteEvent(Long id) {
        calendar.delete(calendar.findById(id).orElseThrow(() -> notFound("Calendar entry")));
    }
}
