package com.rahbar.service;

import com.rahbar.security.Access;
import com.rahbar.security.Section;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Rahbar-e-Banaat > REB Dashboard: charts and figures for the current session, next to the session overview
 * (RebStudentService.dashboard). Girls, attendance, exams, Quran / Hifz, fees, admission enquiries, hostel and staff;
 * each part only for users who may open that REB screen. Counted in the database (grouped queries).
 */
@Service
public class RebDashboardService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    /** Below this attendance a girl is listed as needing attention. */
    static final int LOW_ATTENDANCE = 75;

    private final JdbcTemplate jdbc;

    public RebDashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> insights() {
        // A teacher limited to her own classes uses her own screens; the dashboard figures cover the whole school.
        if (Access.rebClassesScoped()) return null;
        Map<String, Object> session = jdbc.queryForList(
                "SELECT id, name, start_date, end_date FROM reb_sessions WHERE is_current = 1 ORDER BY id DESC LIMIT 1")
                .stream().findFirst().orElse(null);
        LocalDate today = LocalDate.now();
        Long sessionId = session == null ? null : ((Number) session.get("id")).longValue();
        LocalDate from = session == null ? today.minusMonths(11).withDayOfMonth(1) : toDate(session.get("start_date"));
        LocalDate to = session == null ? today : min(toDate(session.get("end_date")), today);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", from);
        out.put("to", to);
        if (can(Section.REB_STUDENTS)) out.put("girls", girls(sessionId, from, to, today));
        if (can(Section.REB_ATTENDANCE)) out.put("attendance", attendance(sessionId, from, today));
        if (can(Section.REB_EXAMS) && sessionId != null) out.put("exams", exams(sessionId, today));
        if (can(Section.REB_QURAN)) out.put("quran", quran(from, today));
        if (can(Section.REB_FEES)) out.put("fees", fees(sessionId, from, to, today));
        if (can(Section.REB_ENQUIRIES)) out.put("enquiries", enquiries(today));
        if (can(Section.REB_HOSTEL)) out.put("hostel", hostel(today));
        if (can(Section.REB_STAFF)) out.put("staff", staff(sessionId));
        return out;
    }

    // ---------------------------------------------------------------- girls

    private Map<String, Object> girls(Long sessionId, LocalDate from, LocalDate to, LocalDate today) {
        Map<String, Object> g = new LinkedHashMap<>();
        if (sessionId != null) {
            g.put("byClass", labelled("""
                    SELECT c.name label, COUNT(*) value FROM reb_enrollments e
                    JOIN reb_sections s ON s.id = e.section_id JOIN reb_classes c ON c.id = s.class_id
                    JOIN reb_students st ON st.id = e.student_id
                    WHERE e.session_id = ? AND e.status = 'ACTIVE' AND st.status = 'ACTIVE'
                    GROUP BY c.id, c.name, c.level_order ORDER BY c.level_order, c.name""", sessionId));
        }
        g.put("age", labelled("""
                SELECT CASE WHEN date_of_birth IS NULL THEN 'Not recorded'
                            WHEN TIMESTAMPDIFF(YEAR, date_of_birth, ?) < 10 THEN 'Under 10'
                            WHEN TIMESTAMPDIFF(YEAR, date_of_birth, ?) < 13 THEN '10–12'
                            WHEN TIMESTAMPDIFF(YEAR, date_of_birth, ?) < 16 THEN '13–15'
                            WHEN TIMESTAMPDIFF(YEAR, date_of_birth, ?) < 19 THEN '16–18'
                            ELSE '19 or older' END label,
                       COUNT(*) value, MIN(COALESCE(TIMESTAMPDIFF(YEAR, date_of_birth, ?), 999)) sort_key
                FROM reb_students WHERE status = 'ACTIVE' GROUP BY label ORDER BY sort_key""", today, today, today, today, today));
        g.put("districts", labelled("""
                SELECT COALESCE(NULLIF(TRIM(district), ''), 'Not recorded') label, COUNT(*) value FROM reb_students
                WHERE status = 'ACTIVE' GROUP BY label ORDER BY value DESC LIMIT 10"""));
        g.put("residence", labelled("""
                SELECT CASE WHEN residential = 1 THEN 'Hostel' ELSE 'Day scholar' END label, COUNT(*) value
                FROM reb_students WHERE status = 'ACTIVE' GROUP BY label ORDER BY label DESC"""));
        g.put("feePlans", labelled("""
                SELECT CASE fee_plan WHEN 'SPONSORED' THEN 'Sponsored' WHEN 'FREE' THEN 'Free' ELSE 'Family pays' END label,
                       COUNT(*) value FROM reb_students WHERE status = 'ACTIVE' GROUP BY label ORDER BY value DESC"""));
        if (can(Section.REB_STUDENT_PRIVATE)) {
            g.put("categories", labelled("""
                    SELECT COALESCE(NULLIF(social_category, ''), 'Not recorded') label, COUNT(*) value FROM reb_students
                    WHERE status = 'ACTIVE' GROUP BY label ORDER BY value DESC"""));
        }
        // Admitted and left per month of the session.
        List<YearMonth> months = months(from, to);
        long[] admitted = new long[months.size()], left = new long[months.size()];
        perMonth("SELECT admitted_on d, COUNT(*) n FROM reb_students WHERE admitted_on BETWEEN ? AND ? GROUP BY admitted_on",
                months, admitted, from, to);
        perMonth("SELECT left_on d, COUNT(*) n FROM reb_students WHERE left_on BETWEEN ? AND ? AND status <> 'ACTIVE' GROUP BY left_on",
                months, left, from, to);
        g.put("joinedLeft", series(labels(months), List.of(serie("Admitted", toList(admitted)), serie("Left", toList(left)))));
        g.put("leavingReasons", labelled("""
                SELECT COALESCE(NULLIF(TRIM(leaving_reason), ''), 'Not recorded') label, COUNT(*) value FROM reb_students
                WHERE status <> 'ACTIVE' AND left_on BETWEEN ? AND ? GROUP BY label ORDER BY value DESC LIMIT 6""", from, to));
        return g;
    }

    // ---------------------------------------------------------------- attendance

    private Map<String, Object> attendance(Long sessionId, LocalDate from, LocalDate today) {
        Map<String, Object> a = new LinkedHashMap<>();
        String present = "SUM(CASE WHEN a.status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END)";
        Map<String, Object> t = jdbc.queryForMap("SELECT COUNT(*) marked, COALESCE(" + present + ", 0) present FROM reb_attendance a WHERE a.date = ?", today);
        a.put("todayMarked", num(t.get("marked")));
        a.put("today", percent(num(t.get("present")), num(t.get("marked"))));
        Map<String, Object> s = jdbc.queryForMap("SELECT COUNT(*) marked, COALESCE(" + present + ", 0) present FROM reb_attendance a WHERE a.date BETWEEN ? AND ?", from, today);
        a.put("session", percent(num(s.get("present")), num(s.get("marked"))));
        a.put("schoolDays", num(jdbc.queryForObject("SELECT COUNT(DISTINCT date) FROM reb_attendance WHERE date BETWEEN ? AND ?", Long.class, from, today)));

        // Last 30 days, only days that were marked.
        List<String> days = new ArrayList<>();
        List<Integer> dayPct = new ArrayList<>();
        jdbc.queryForList("SELECT a.date d, COUNT(*) marked, " + present + " present FROM reb_attendance a WHERE a.date BETWEEN ? AND ? GROUP BY a.date ORDER BY a.date",
                today.minusDays(29), today).forEach(r -> {
            days.add(toDate(r.get("d")).format(DAY));
            dayPct.add(percent(num(r.get("present")), num(r.get("marked"))));
        });
        a.put("last30", series(days, List.of(serie("Present %", dayPct))));

        List<YearMonth> months = months(from, today);
        Integer[] monthPct = new Integer[months.size()];
        Arrays.fill(monthPct, 0);
        jdbc.queryForList("SELECT YEAR(a.date) y, MONTH(a.date) m, COUNT(*) marked, " + present + " present FROM reb_attendance a "
                + "WHERE a.date BETWEEN ? AND ? GROUP BY y, m", from, today).forEach(r -> {
            int i = months.indexOf(YearMonth.of((int) num(r.get("y")), (int) num(r.get("m"))));
            if (i >= 0) monthPct[i] = percent(num(r.get("present")), num(r.get("marked")));
        });
        a.put("byMonth", series(labels(months), List.of(serie("Present %", Arrays.asList(monthPct)))));

        LocalDate monthStart = today.withDayOfMonth(1);
        a.put("byClassThisMonth", rows("""
                SELECT c.name label, COUNT(*) marked, %s present FROM reb_attendance a
                JOIN reb_sections s ON s.id = a.section_id JOIN reb_classes c ON c.id = s.class_id
                WHERE a.date BETWEEN ? AND ? GROUP BY c.id, c.name, c.level_order ORDER BY c.level_order, c.name""".formatted(present),
                monthStart, today).stream().map(r -> point(String.valueOf(r.get("label")), percent(num(r.get("present")), num(r.get("marked"))))).toList());
        a.put("byStatus", labelled("""
                SELECT CASE a.status WHEN 'PRESENT' THEN 'Present' WHEN 'LATE' THEN 'Late' WHEN 'LEAVE' THEN 'On leave' ELSE 'Absent' END label,
                       COUNT(*) value FROM reb_attendance a WHERE a.date BETWEEN ? AND ? GROUP BY label ORDER BY value DESC""", from, today));

        // Girls below LOW_ATTENDANCE % this session (at least 5 days marked), lowest first.
        List<Map<String, Object>> low = rows("""
                SELECT st.id, st.name, COALESCE(c.name, '') class_name, COUNT(*) marked, %s present
                FROM reb_attendance a JOIN reb_students st ON st.id = a.student_id
                LEFT JOIN reb_sections s ON s.id = a.section_id LEFT JOIN reb_classes c ON c.id = s.class_id
                WHERE a.date BETWEEN ? AND ? AND st.status = 'ACTIVE'
                GROUP BY st.id, st.name, c.name HAVING COUNT(*) >= 5 AND %s * 100 < ? * COUNT(*)
                ORDER BY %s / COUNT(*), st.name""".formatted(present, present, present), from, today, LOW_ATTENDANCE);
        a.put("lowCount", low.size());
        a.put("low", low.stream().limit(10).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("class_name", r.get("class_name"));
            m.put("percent", percent(num(r.get("present")), num(r.get("marked"))));
            m.put("days", num(r.get("marked")));
            return m;
        }).toList());
        a.put("lowThreshold", LOW_ATTENDANCE);
        return a;
    }

    // ---------------------------------------------------------------- exams

    private Map<String, Object> exams(long sessionId, LocalDate today) {
        Map<String, Object> e = new LinkedHashMap<>();
        Map<String, Long> status = new LinkedHashMap<>();
        for (String st : List.of("DRAFT", "MARKS_ENTRY", "PUBLISHED")) status.put(st, 0L);
        jdbc.queryForList("SELECT status, COUNT(*) n FROM reb_exams WHERE session_id = ? GROUP BY status", sessionId)
                .forEach(r -> status.put(String.valueOf(r.get("status")), num(r.get("n"))));
        e.put("status", status);
        e.put("upcoming", rows("""
                SELECT id, name, kind, start_date, end_date, status FROM reb_exams
                WHERE session_id = ? AND (start_date >= ? OR (end_date IS NOT NULL AND end_date >= ?)) ORDER BY start_date LIMIT 5""",
                sessionId, today, today));
        // Marks entered so far for exams open for marks: entered marks of all expected (girls in the class x papers).
        e.put("marksEntry", rows("""
                SELECT x.id, x.name,
                       (SELECT COUNT(*) FROM reb_marks m JOIN reb_exam_papers p2 ON p2.id = m.paper_id WHERE p2.exam_id = x.id) entered,
                       (SELECT COALESCE(SUM((SELECT COUNT(*) FROM reb_enrollments en JOIN reb_sections se ON se.id = en.section_id
                                             WHERE se.class_id = p.class_id AND en.session_id = x.session_id AND en.status = 'ACTIVE')), 0)
                          FROM reb_exam_papers p WHERE p.exam_id = x.id) expected
                FROM reb_exams x WHERE x.session_id = ? AND x.status = 'MARKS_ENTRY' ORDER BY x.start_date""", sessionId));

        // Average and pass rate of each published exam, in order: is the school improving?
        List<String> names = new ArrayList<>();
        List<Integer> avg = new ArrayList<>(), pass = new ArrayList<>();
        jdbc.queryForList("""
                SELECT x.name, AVG(r.percent) avg_percent, COUNT(*) n, SUM(CASE WHEN r.result = 'PASS' THEN 1 ELSE 0 END) passed
                FROM reb_exams x JOIN reb_exam_results r ON r.exam_id = x.id
                WHERE x.session_id = ? AND x.status = 'PUBLISHED' GROUP BY x.id, x.name, x.start_date ORDER BY x.start_date, x.id""", sessionId)
                .forEach(r -> {
                    names.add(String.valueOf(r.get("name")));
                    avg.add(round(r.get("avg_percent")));
                    pass.add(percent(num(r.get("passed")), num(r.get("n"))));
                });
        e.put("trend", series(names, List.of(serie("Average %", avg), serie("Pass rate %", pass))));

        Map<String, Object> latest = jdbc.queryForList("""
                SELECT id, name, kind, published_at FROM reb_exams WHERE session_id = ? AND status = 'PUBLISHED'
                ORDER BY COALESCE(published_at, end_date, start_date) DESC, id DESC LIMIT 1""", sessionId).stream().findFirst().orElse(null);
        if (latest != null) {
            long examId = num(latest.get("id"));
            Map<String, Object> l = new LinkedHashMap<>(latest);
            Map<String, Object> sum = jdbc.queryForMap("""
                    SELECT COUNT(*) n, COALESCE(SUM(CASE WHEN result = 'PASS' THEN 1 ELSE 0 END), 0) passed, AVG(percent) avg_percent,
                           MAX(percent) top_percent,
                           AVG(CASE WHEN madrasa_max > 0 THEN madrasa_total * 100 / madrasa_max END) madrasa,
                           AVG(CASE WHEN school_max > 0 THEN school_total * 100 / school_max END) school
                    FROM reb_exam_results WHERE exam_id = ?""", examId);
            l.put("girls", num(sum.get("n")));
            l.put("passed", num(sum.get("passed")));
            l.put("failed", num(sum.get("n")) - num(sum.get("passed")));
            l.put("passRate", percent(num(sum.get("passed")), num(sum.get("n"))));
            l.put("average", round(sum.get("avg_percent")));
            l.put("top", round(sum.get("top_percent")));
            l.put("madrasaAverage", round(sum.get("madrasa")));
            l.put("schoolAverage", round(sum.get("school")));
            l.put("absentPapers", num(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM reb_marks m JOIN reb_exam_papers p ON p.id = m.paper_id WHERE p.exam_id = ? AND m.absent = 1""",
                    Long.class, examId)));
            l.put("grades", labelled("""
                    SELECT COALESCE(NULLIF(grade, ''), '–') label, COUNT(*) value, MAX(percent) sort_key FROM reb_exam_results
                    WHERE exam_id = ? GROUP BY label ORDER BY sort_key DESC""", examId));
            l.put("byClass", rows("""
                    SELECT c.name label, AVG(r.percent) value FROM reb_exam_results r JOIN reb_sections s ON s.id = r.section_id
                    JOIN reb_classes c ON c.id = s.class_id WHERE r.exam_id = ?
                    GROUP BY c.id, c.name, c.level_order ORDER BY c.level_order, c.name""", examId)
                    .stream().map(r -> point(String.valueOf(r.get("label")), round(r.get("value")))).toList());
            l.put("bySubject", rows("""
                    SELECT sub.name label, SUM(m.marks) * 100 / SUM(p.max_marks) value FROM reb_marks m
                    JOIN reb_exam_papers p ON p.id = m.paper_id JOIN reb_subjects sub ON sub.id = p.subject_id
                    WHERE p.exam_id = ? AND m.absent = 0 AND m.marks IS NOT NULL AND p.max_marks > 0
                    GROUP BY sub.id, sub.name ORDER BY value DESC""", examId)
                    .stream().map(r -> point(String.valueOf(r.get("label")), round(r.get("value")))).toList());
            l.put("toppers", rows("""
                    SELECT st.id, st.name, COALESCE(CONCAT(c.name, ' ', s.name), '') class_name, r.percent, r.grade
                    FROM reb_exam_results r JOIN reb_students st ON st.id = r.student_id
                    LEFT JOIN reb_sections s ON s.id = r.section_id LEFT JOIN reb_classes c ON c.id = s.class_id
                    WHERE r.exam_id = ? ORDER BY r.percent DESC, st.name LIMIT 5""", examId));
            e.put("latest", l);
        }
        return e;
    }

    // ---------------------------------------------------------------- Quran / Hifz

    private Map<String, Object> quran(LocalDate from, LocalDate today) {
        Map<String, Object> q = new LinkedHashMap<>();
        LocalDate since = today.minusDays(29);
        q.put("lessons30", num(jdbc.queryForObject("SELECT COUNT(*) FROM reb_quran_progress WHERE date BETWEEN ? AND ?", Long.class, since, today)));
        q.put("girls30", num(jdbc.queryForObject("SELECT COUNT(DISTINCT student_id) FROM reb_quran_progress WHERE date BETWEEN ? AND ?", Long.class, since, today)));
        q.put("parasCompleted", num(jdbc.queryForObject(
                "SELECT COUNT(*) FROM reb_quran_progress WHERE completed_para = 1 AND date BETWEEN ? AND ?", Long.class, from, today)));
        q.put("byKind", labelled("""
                SELECT CASE kind WHEN 'SABAQ' THEN 'Sabaq (new)' WHEN 'SABQI' THEN 'Sabqi (recent)' ELSE 'Manzil (revision)' END label,
                       COUNT(*) value FROM reb_quran_progress WHERE date BETWEEN ? AND ? GROUP BY label ORDER BY value DESC""", since, today));
        q.put("quality", labelled("""
                SELECT CASE quality WHEN 'EXCELLENT' THEN 'Excellent' WHEN 'GOOD' THEN 'Good' ELSE 'Needs revision' END label,
                       COUNT(*) value FROM reb_quran_progress WHERE date BETWEEN ? AND ? GROUP BY label ORDER BY value DESC""", since, today));
        // How far each girl has reached (highest para with a lesson), in groups of five paras.
        q.put("parasReached", labelled("""
                SELECT CONCAT('Para ', (FLOOR((top_para - 1) / 5) * 5 + 1), '–', (FLOOR((top_para - 1) / 5) * 5 + 5)) label,
                       COUNT(*) value, MIN(top_para) sort_key
                FROM (SELECT q.student_id, MAX(q.para) top_para FROM reb_quran_progress q JOIN reb_students st ON st.id = q.student_id
                      WHERE st.status = 'ACTIVE' AND q.para BETWEEN 1 AND 30 GROUP BY q.student_id) t
                GROUP BY label ORDER BY sort_key"""));
        q.put("noLesson14", num(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT q.student_id) FROM reb_quran_progress q JOIN reb_students st ON st.id = q.student_id
                WHERE st.status = 'ACTIVE' AND q.date >= ? AND q.student_id NOT IN
                      (SELECT student_id FROM reb_quran_progress WHERE date > ?)""", Long.class, from, today.minusDays(14))));
        return q;
    }

    // ---------------------------------------------------------------- fees

    private Map<String, Object> fees(Long sessionId, LocalDate from, LocalDate to, LocalDate today) {
        Map<String, Object> f = new LinkedHashMap<>();
        List<YearMonth> months = months(from, to);
        BigDecimal[] billed = zeros(months.size()), collected = zeros(months.size());
        jdbc.queryForList("SELECT period, SUM(net) amount FROM reb_invoices WHERE status <> 'CANCELLED' AND period BETWEEN ? AND ? GROUP BY period",
                YearMonth.from(from).toString(), YearMonth.from(to).toString()).forEach(r -> {
            int i = months.indexOf(YearMonth.parse(String.valueOf(r.get("period"))));
            if (i >= 0) billed[i] = money(r.get("amount"));
        });
        jdbc.queryForList("SELECT YEAR(paid_on) y, MONTH(paid_on) m, SUM(amount) amount FROM reb_payments "
                + "WHERE status = 'VALID' AND paid_on BETWEEN ? AND ? GROUP BY y, m", from, to).forEach(r -> {
            int i = months.indexOf(YearMonth.of((int) num(r.get("y")), (int) num(r.get("m"))));
            if (i >= 0) collected[i] = money(r.get("amount"));
        });
        f.put("byMonth", series(labels(months), List.of(serie("Billed", Arrays.asList(billed)), serie("Collected", Arrays.asList(collected)))));

        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(net), 0) billed, COALESCE(SUM(sponsor_share), 0) sponsor_share, COALESCE(SUM(family_share), 0) family_share,
                       COALESCE(SUM(sponsor_paid + family_paid), 0) paid, COALESCE(SUM(concession), 0) concession
                FROM reb_invoices WHERE status NOT IN ('CANCELLED', 'WAIVED') AND period BETWEEN ? AND ?""",
                YearMonth.from(from).toString(), YearMonth.from(to).toString());
        BigDecimal billedTotal = money(totals.get("billed")), paidTotal = money(totals.get("paid"));
        f.put("billed", billedTotal);
        f.put("paid", paidTotal);
        f.put("concessions", money(totals.get("concession")));
        f.put("collectionRate", billedTotal.signum() == 0 ? null
                : paidTotal.multiply(BigDecimal.valueOf(100)).divide(billedTotal, 0, RoundingMode.HALF_UP).intValue());
        f.put("shares", List.of(point("Sponsors", money(totals.get("sponsor_share"))), point("Families", money(totals.get("family_share")))));
        f.put("byPayer", labelled("""
                SELECT CASE payer WHEN 'SPONSOR' THEN 'Sponsors' ELSE 'Families' END label, COALESCE(SUM(amount), 0) value
                FROM reb_payments WHERE status = 'VALID' AND paid_on BETWEEN ? AND ? GROUP BY label ORDER BY value DESC""", from, to));
        f.put("methods", labelled("""
                SELECT CASE method WHEN 'BANK' THEN 'Bank transfer' WHEN 'UPI' THEN 'UPI' WHEN 'CHEQUE' THEN 'Cheque' ELSE 'Cash' END label,
                       COALESCE(SUM(amount), 0) value FROM reb_payments WHERE status = 'VALID' AND paid_on BETWEEN ? AND ?
                GROUP BY label ORDER BY value DESC""", from, to));
        String balance = "(i.net - i.sponsor_paid - i.family_paid)";
        f.put("overdueAgeing", List.of(jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN i.due_date >= ? THEN %1$s END), 0) b1,
                       COALESCE(SUM(CASE WHEN i.due_date < ? AND i.due_date >= ? THEN %1$s END), 0) b2,
                       COALESCE(SUM(CASE WHEN i.due_date < ? THEN %1$s END), 0) b3
                FROM reb_invoices i WHERE i.status IN ('DUE', 'PARTIAL') AND i.due_date < ?""".formatted(balance),
                today.minusDays(30), today.minusDays(30), today.minusDays(90), today.minusDays(90), today))
                .stream().flatMap(r -> List.of(point("Up to 30 days", money(r.get("b1"))), point("31–90 days", money(r.get("b2"))),
                        point("Over 90 days", money(r.get("b3")))).stream()).toList());
        if (sessionId != null) {
            f.put("openByClass", rows("""
                    SELECT c.name label, SUM(%s) value FROM reb_invoices i
                    JOIN reb_enrollments e ON e.student_id = i.student_id AND e.session_id = ?
                    JOIN reb_sections s ON s.id = e.section_id JOIN reb_classes c ON c.id = s.class_id
                    WHERE i.status IN ('DUE', 'PARTIAL') GROUP BY c.id, c.name, c.level_order ORDER BY c.level_order, c.name""".formatted(balance),
                    sessionId).stream().map(r -> point(String.valueOf(r.get("label")), money(r.get("value")))).toList());
        }
        return f;
    }

    // ---------------------------------------------------------------- enquiries, hostel, staff

    private Map<String, Object> enquiries(LocalDate today) {
        Map<String, Object> q = new LinkedHashMap<>();
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT COUNT(*) total, COALESCE(SUM(CASE WHEN status <> 'NEW' THEN 1 ELSE 0 END), 0) contacted,
                       COALESCE(SUM(CASE WHEN status IN ('TEST', 'ADMITTED', 'NOT_ADMITTED') THEN 1 ELSE 0 END), 0) tested,
                       COALESCE(SUM(CASE WHEN status = 'ADMITTED' THEN 1 ELSE 0 END), 0) admitted,
                       COALESCE(SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END), 0) waiting
                FROM reb_admission_enquiries WHERE created_at >= ?""", today.minusMonths(12).atStartOfDay());
        q.put("waiting", num(c.get("waiting")));
        q.put("funnel", List.of(point("Enquiries", c.get("total")), point("Contacted", c.get("contacted")),
                point("Test / decided", c.get("tested")), point("Admitted", c.get("admitted"))));
        q.put("bySource", labelled("""
                SELECT CASE source WHEN 'WEBSITE' THEN 'Website' WHEN 'PHONE' THEN 'Phone' ELSE 'Walk-in' END label, COUNT(*) value
                FROM reb_admission_enquiries WHERE created_at >= ? GROUP BY label ORDER BY value DESC""", today.minusMonths(12).atStartOfDay()));
        q.put("byClass", labelled("""
                SELECT COALESCE(NULLIF(TRIM(class_sought), ''), 'Not given') label, COUNT(*) value FROM reb_admission_enquiries
                WHERE created_at >= ? GROUP BY label ORDER BY value DESC LIMIT 8""", today.minusMonths(12).atStartOfDay()));
        List<YearMonth> months = months(today.minusMonths(11).withDayOfMonth(1), today);
        long[] perMonth = new long[months.size()];
        jdbc.queryForList("SELECT YEAR(created_at) y, MONTH(created_at) m, COUNT(*) n FROM reb_admission_enquiries WHERE created_at >= ? GROUP BY y, m",
                months.get(0).atDay(1).atStartOfDay()).forEach(r -> {
            int i = months.indexOf(YearMonth.of((int) num(r.get("y")), (int) num(r.get("m"))));
            if (i >= 0) perMonth[i] = num(r.get("n"));
        });
        q.put("byMonth", series(labels(months), List.of(serie("Enquiries", toList(perMonth)))));
        return q;
    }

    private Map<String, Object> hostel(LocalDate today) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("outNow", num(jdbc.queryForObject("SELECT COUNT(*) FROM reb_hostel_leaves WHERE status = 'OUT'", Long.class)));
        h.put("lateBack", num(jdbc.queryForObject("SELECT COUNT(*) FROM reb_hostel_leaves WHERE status = 'OUT' AND leave_to < ?", Long.class, today)));
        h.put("thisMonth", num(jdbc.queryForObject("SELECT COUNT(*) FROM reb_hostel_leaves WHERE leave_from >= ?", Long.class, today.withDayOfMonth(1))));
        return h;
    }

    private Map<String, Object> staff(Long sessionId) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("byRole", labelled("""
                SELECT CASE designation WHEN 'TEACHER' THEN 'Teachers' WHEN 'PRINCIPAL' THEN 'Principal' WHEN 'WARDEN' THEN 'Wardens'
                            WHEN 'OFFICE' THEN 'Office' ELSE 'Other' END label, COUNT(*) value
                FROM reb_staff WHERE status = 'ACTIVE' GROUP BY label ORDER BY value DESC"""));
        if (sessionId != null) {
            // Subjects a section studies that no teacher has been given yet.
            s.put("subjectsWithoutTeacher", num(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM reb_class_subjects cs JOIN reb_sections se ON se.class_id = cs.class_id AND se.session_id = cs.session_id
                    WHERE cs.session_id = ? AND NOT EXISTS (SELECT 1 FROM reb_teacher_assignments t
                          WHERE t.section_id = se.id AND t.subject_id = cs.subject_id AND t.session_id = cs.session_id)""", Long.class, sessionId)));
            s.put("girlsPerTeacher", jdbc.queryForMap("""
                    SELECT (SELECT COUNT(*) FROM reb_enrollments WHERE session_id = ? AND status = 'ACTIVE') girls,
                           (SELECT COUNT(*) FROM reb_staff WHERE status = 'ACTIVE' AND designation IN ('TEACHER', 'PRINCIPAL')) teachers""", sessionId));
        }
        return s;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean can(Section section) {
        return Access.can(section, Section.Level.VIEW);
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private List<Map<String, Object>> labelled(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(r -> point(String.valueOf(r.get("label")), r.get("value"))).toList();
    }

    /** Adds per-day counts ("d", "n") into the month they fall in. */
    private void perMonth(String sql, List<YearMonth> months, long[] into, Object... args) {
        jdbc.queryForList(sql, args).forEach(r -> {
            int i = months.indexOf(YearMonth.from(toDate(r.get("d"))));
            if (i >= 0) into[i] += num(r.get("n"));
        });
    }

    /** Every month from the month of {@code from} to the month of {@code to} (at most 24). */
    static List<YearMonth> months(LocalDate from, LocalDate to) {
        List<YearMonth> out = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)) && out.size() < 24; m = m.plusMonths(1)) out.add(m);
        return out;
    }

    private static List<String> labels(List<YearMonth> months) {
        return months.stream().map(m -> m.atDay(1).format(MONTH)).toList();
    }

    static Map<String, Object> point(String label, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value instanceof BigDecimal || value instanceof Long || value instanceof Integer ? value
                : value instanceof Number n ? (Object) n.longValue() : 0L);
        return m;
    }

    private static Map<String, Object> serie(String name, List<?> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("values", values);
        return m;
    }

    private static Map<String, Object> series(List<String> labels, List<Map<String, Object>> series) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", labels);
        m.put("series", series);
        return m;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static List<Long> toList(long[] values) {
        List<Long> list = new ArrayList<>();
        for (long v : values) list.add(v);
        return list;
    }

    static Integer percent(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }

    private static Integer round(Object v) {
        return v instanceof Number n ? (int) Math.round(n.doubleValue()) : null;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static BigDecimal money(Object o) {
        if (o instanceof BigDecimal b) return b;
        return o instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.time.LocalDateTime t) return t.toLocalDate();
        return LocalDate.parse(String.valueOf(o).substring(0, 10));
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
