package com.rahbar.service;

import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.entity.User;
import com.rahbar.repository.ApplicationPeriodRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.util.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The admin dashboards: Overview (Admin > Dashboard) and one per big module (Students, Sponsors &amp; Payments,
 * Applications). Everything is counted in the database (a few grouped queries per dashboard), so they stay fast
 * however many students and payments there are.
 *
 * <p>Each part only appears for users allowed to see the screen it summarises (money for Payment Records, income
 * for Finance, ...). Users whose role covers one chapter or RCC center only get their own students counted.
 * Sponsors are only ever shown by user ID and name.
 */
@Service
public class DashboardService {

    private static final String STUDY = "COALESCE(NULLIF(s.study_status, ''), 'STUDYING')";
    private static final String IN_PROGRAMME = " AND " + STUDY + " IN ('STUDYING', 'ON_HOLD')";
    private static final String STUDENT = "s.role_id = 6 AND s.merged_into_id IS NULL";
    private static final String SPONSOR_ROLE_IDS = "4, 5";
    /** Scholarship applications (since V45) with the student account they became, if accepted. */
    private static final String APPS = " FROM scholarship_applications g LEFT JOIN users s ON s.id = g.student_user_id WHERE 1 = 1";
    /** Application statuses still waiting for a decision. */
    static final Set<String> OPEN = ApplicationService.OPEN_STATUSES;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ApplicationPeriodRepository periods;

    public DashboardService(JdbcTemplate jdbc, UserRepository users, ApplicationPeriodRepository periods) {
        this.jdbc = jdbc;
        this.users = users;
        this.periods = periods;
    }

    // ================================================================ Overview

    public Map<String, Object> overview(Integer requestedYear) {
        LocalDate today = LocalDate.now();
        int year = requestedYear == null ? today.getYear() : requestedYear;
        String scope = scope("s");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("year", year);
        body.put("years", years("SELECT DISTINCT YEAR(payment_date) FROM payments WHERE payment_date IS NOT NULL", today.getYear()));
        body.put("scope", scopeLabel());

        Map<String, Long> study = studyStatusCounts(scope);
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("studying", study.getOrDefault(StudyStatus.STUDYING, 0L));
        kpis.put("onHold", study.getOrDefault(StudyStatus.ON_HOLD, 0L));
        kpis.put("graduated", study.getOrDefault(StudyStatus.GRADUATED, 0L));
        if (can(Section.SPONSORSHIPS)) {
            kpis.put("activeSponsors", activeSponsors());
            kpis.put("sponsorsWithStudents", sponsorsWithStudents(scope));
        }
        boolean money = can(Section.PAYMENT_RECORDS) || can(Section.PAYMENT_DUES);
        if (money) {
            kpis.put("paidThisYear", paidBetween(scope, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1)));
            kpis.put("paidLastYear", paidLastYear(scope, year, today));
            kpis.putAll(collection(scope, year, today));
            Map<String, Object> overdue = overdue(scope, today);
            kpis.put("overdueInstallments", overdue.get("installments"));
            kpis.put("overdueStudents", overdue.get("students"));
        }
        if (can(Section.ONLINE_PAYMENTS)) {
            Map<String, Object> d = jdbc.queryForMap("""
                    SELECT COUNT(*) n, COALESCE(SUM(amount), 0) amount FROM online_payments
                    WHERE status = 'PAID' AND purpose = 'DONATION' AND paid_at >= ? AND paid_at < ?""",
                    yearStart(year), yearStart(year + 1));
            kpis.put("donations", d.get("amount"));
            kpis.put("donationsCount", d.get("n"));
        }
        if (can(Section.ALUMNI)) kpis.put("alumni", count("SELECT COUNT(*) FROM alumni_profiles"));
        if (can(Section.MENTORING)) kpis.put("activeMentorships", count("SELECT COUNT(*) FROM mentorships WHERE status = 'ACTIVE'"));
        body.put("kpis", kpis);
        body.put("attention", attention(scope, today));

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("studyStatus", studyStatusChart(study));
        if (money) {
            charts.put("collectedByMonth", collectedByMonth(scope, year));
            charts.put("overdueAgeing", overdueAgeing(scope, today));
        }
        if (can(Section.APPLICATIONS)) charts.put("applicationFunnel", funnel(placeScope("s"), year));
        charts.put("studentsByChapter", labelled("""
                SELECT COALESCE(c.chapter_name, 'No chapter') label, COUNT(*) value FROM users s
                LEFT JOIN chapters c ON c.chapter_id = s.chapter_id
                WHERE %s%s%s GROUP BY label ORDER BY value DESC LIMIT 12""".formatted(STUDENT, IN_PROGRAMME, scope)));
        body.put("charts", charts);

        if (can(Section.REB_DASHBOARD)) body.put("reb", reb(today));
        if (can(Section.APPLICATION_PERIOD) || can(Section.APPLICATIONS)) body.put("applicationPeriod", period());
        if (can(Section.ACTIVITY)) {
            body.put("recentActivity", jdbc.queryForList("""
                    SELECT user_name, action, created_at FROM activity_log ORDER BY log_id DESC LIMIT 8"""));
        }
        return body;
    }

    /** Work waiting for someone, each with a link to the screen where it is done. Only screens the user may open. */
    private List<Map<String, Object>> attention(String scope, LocalDate today) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (can(Section.APPLICATIONS)) {
            long open = 0;
            for (Map.Entry<String, Long> e : applicationStatusCounts(placeScope("s"), null).entrySet()) {
                if (OPEN.contains(e.getKey())) open += e.getValue();
            }
            items.add(item("applications", "Applications waiting for a decision", open, null, "/admin/applications"));
        }
        if (can(Section.PAYMENT_RECORDS)) {
            Map<String, Object> p = jdbc.queryForMap("""
                    SELECT COUNT(*) n FROM payments p JOIN users s ON s.id = p.grantee_id
                    WHERE LOWER(p.status) = 'pending'""" + scope);
            items.add(item("pendingPayments", "Payments waiting for approval", num(p.get("n")), null, "/admin/payment-records"));
        }
        if (can(Section.PAYMENT_DUES) || can(Section.PAYMENT_RECORDS)) {
            Map<String, Object> o = overdue(scope, today);
            items.add(item("overdue", "Overdue installments", num(o.get("installments")), null, "/admin/payment-dues"));
        }
        if (can(Section.SPONSORSHIPS)) {
            items.add(item("withoutSponsor", "Studying students without a sponsor", withoutSponsor(scope), null, "/admin/sponsorships"));
            items.add(item("lapsedSponsors", "Sponsors more than 30 days behind", lapsedSponsors(scope, today), null, "/admin/sponsor-followup"));
        }
        if (can(Section.STUDENTS)) {
            items.add(item("withoutBank", "Studying students without bank details", count("""
                    SELECT COUNT(*) FROM users s WHERE %s AND s.status = 'Active' AND %s = 'STUDYING'%s
                    AND NOT EXISTS (SELECT 1 FROM bank_details b WHERE b.user_id = s.id)""".formatted(STUDENT, STUDY, scope)),
                    null, "/admin/students"));
            items.add(item("progress", "Progress reports to review", count("""
                    SELECT COUNT(*) FROM student_progress p JOIN users s ON s.id = p.grantee_id
                    WHERE p.review_status = 'Pending'""" + scope), null, "/admin/students"));
        }
        if (can(Section.STUDENT_NOTES)) {
            items.add(item("notes", "Notes to sponsors to approve", count("""
                    SELECT COUNT(*) FROM student_notes n JOIN users s ON s.id = n.student_user_id
                    WHERE n.status = 'PENDING'""" + scope), null, "/admin/student-notes"));
        }
        if (can(Section.ONLINE_PAYMENTS)) {
            Map<String, Object> o = jdbc.queryForMap("""
                    SELECT COUNT(*) n, COALESCE(SUM(i.amount), 0) amount FROM online_payment_items i
                    JOIN online_payments p ON p.id = i.online_payment_id
                    WHERE p.status = 'PAID' AND i.passed_on_at IS NULL""");
            items.add(item("passOn", "Online payments to pass on to students", num(o.get("n")), (BigDecimal) o.get("amount"), "/admin/online-payments"));
        }
        if (can(Section.VOLUNTEERS)) {
            items.add(item("volunteers", "New volunteer sign-ups", count("SELECT COUNT(*) FROM volunteer_applications WHERE status = 'NEW'"),
                    null, "/admin/volunteers"));
        }
        if (can(Section.WEBSITE_MESSAGES)) {
            items.add(item("messages", "Website messages not handled", count("SELECT COUNT(*) FROM web_messages WHERE handled = 0"),
                    null, "/admin/website/messages"));
        }
        return items;
    }

    // ================================================================ Students

    public Map<String, Object> students() {
        LocalDate today = LocalDate.now();
        String scope = scope("s");
        Map<String, Long> study = studyStatusCounts(scope);
        long inProgramme = study.getOrDefault(StudyStatus.STUDYING, 0L) + study.getOrDefault(StudyStatus.ON_HOLD, 0L);
        long graduated = study.getOrDefault(StudyStatus.GRADUATED, 0L);
        long dropped = study.getOrDefault(StudyStatus.DROPPED_OUT, 0L);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("studying", study.getOrDefault(StudyStatus.STUDYING, 0L));
        kpis.put("onHold", study.getOrDefault(StudyStatus.ON_HOLD, 0L));
        kpis.put("graduated", graduated);
        kpis.put("droppedOut", dropped);
        kpis.put("completionRate", percent(graduated, graduated + dropped));
        kpis.put("withoutSponsor", withoutSponsor(scope));
        kpis.put("withoutCourse", count("""
                SELECT COUNT(*) FROM users s WHERE %s%s%s
                AND NOT EXISTS (SELECT 1 FROM student_institution_courses c WHERE c.user_id = s.id)""".formatted(STUDENT, IN_PROGRAMME, scope)));
        kpis.put("withoutBank", count("""
                SELECT COUNT(*) FROM users s WHERE %s%s%s
                AND NOT EXISTS (SELECT 1 FROM bank_details b WHERE b.user_id = s.id)""".formatted(STUDENT, IN_PROGRAMME, scope)));
        long mentored = count("""
                SELECT COUNT(DISTINCT m.student_user_id) FROM mentorships m JOIN users s ON s.id = m.student_user_id
                WHERE m.status = 'ACTIVE' AND %s%s%s""".formatted(STUDENT, IN_PROGRAMME, scope));
        kpis.put("mentored", mentored);
        kpis.put("mentoredRate", percent(mentored, inProgramme));

        // Progress reports for this year's session: who sent one, and how many are still to be reviewed.
        int year = today.getYear();
        Map<String, Object> progress = jdbc.queryForMap("""
                SELECT COUNT(DISTINCT p.grantee_id) students, COUNT(*) reports,
                       COALESCE(SUM(CASE WHEN p.review_status = 'Pending' THEN 1 ELSE 0 END), 0) pending
                FROM student_progress p JOIN users s ON s.id = p.grantee_id
                WHERE p.year = ? AND %s%s%s""".formatted(STUDENT, IN_PROGRAMME, scope), year);
        progress.put("year", year);
        progress.put("expected", study.getOrDefault(StudyStatus.STUDYING, 0L));

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("studyStatus", studyStatusChart(study));
        charts.put("bySessionYear", labelled("""
                SELECT COALESCE(CAST(s.year AS CHAR), 'Not set') label, COUNT(*) value FROM users s
                WHERE %s%s%s GROUP BY s.year ORDER BY s.year""".formatted(STUDENT, IN_PROGRAMME, scope)));
        charts.put("bySex", labelled("""
                SELECT CASE s.sex WHEN 'F' THEN 'Female' WHEN 'M' THEN 'Male' ELSE 'Not recorded' END label, COUNT(*) value
                FROM users s WHERE %s%s%s GROUP BY label ORDER BY value DESC""".formatted(STUDENT, IN_PROGRAMME, scope)));
        charts.put("byChapter", labelled("""
                SELECT COALESCE(c.chapter_name, 'No chapter') label, COUNT(*) value FROM users s
                LEFT JOIN chapters c ON c.chapter_id = s.chapter_id
                WHERE %s%s%s GROUP BY label ORDER BY value DESC LIMIT 15""".formatted(STUDENT, IN_PROGRAMME, scope)));
        charts.put("byInstitution", labelled("""
                SELECT COALESCE(i.institution_name, 'Not assigned') label, COUNT(*) value FROM users s
                LEFT JOIN student_institution_courses sc ON sc.user_id = s.id
                LEFT JOIN institutions i ON i.institution_id = sc.institution_id
                WHERE %s%s%s GROUP BY label ORDER BY value DESC LIMIT 12""".formatted(STUDENT, IN_PROGRAMME, scope)));
        charts.put("byCourse", labelled("""
                SELECT COALESCE(co.course_name, 'Not assigned') label, COUNT(*) value FROM users s
                LEFT JOIN student_institution_courses sc ON sc.user_id = s.id
                LEFT JOIN courses co ON co.course_id = sc.course_id
                WHERE %s%s%s GROUP BY label ORDER BY value DESC LIMIT 12""".formatted(STUDENT, IN_PROGRAMME, scope)));
        charts.put("intakeAndExits", intakeAndExits(scope, today.getYear()));
        charts.put("progressReviews", labelled("""
                SELECT p.review_status label, COUNT(*) value FROM student_progress p JOIN users s ON s.id = p.grantee_id
                WHERE p.year = ? AND %s%s GROUP BY p.review_status ORDER BY value DESC""".formatted(STUDENT, scope), year));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", scopeLabel());
        body.put("kpis", kpis);
        body.put("progress", progress);
        body.put("charts", charts);
        return body;
    }

    /** Per year (last 8): students who joined, graduated and dropped out. */
    /**
     * Students joined (by session year: the batch they belong to, not when their record was typed into Rahbar),
     * graduated and dropped out (by the date of that study status) per year: up to 8 years, ending at the latest year
     * with any of them (not an empty current year), at most this year.
     */
    private Map<String, Object> intakeAndExits(String scope, int thisYear) {
        Integer latestIntake = jdbc.queryForObject("SELECT MAX(s.year) FROM users s WHERE %s%s AND s.year <= ?"
                .formatted(STUDENT, scope), Integer.class, thisYear);
        Integer latestExit = jdbc.queryForObject("""
                SELECT MAX(YEAR(s.study_status_date)) FROM users s
                WHERE %s%s AND s.study_status IN ('GRADUATED', 'DROPPED_OUT') AND s.study_status_date <= ?"""
                .formatted(STUDENT, scope), Integer.class, LocalDate.of(thisYear, 12, 31));
        int last = Math.max(latestIntake == null ? 0 : latestIntake, latestExit == null ? 0 : latestExit);
        if (last == 0) last = thisYear;
        int from = last - 7;
        Map<Integer, long[]> byYear = new TreeMap<>();
        for (int y = from; y <= last; y++) byYear.put(y, new long[3]);
        jdbc.queryForList("""
                SELECT s.year y, COUNT(*) n FROM users s WHERE %s%s AND s.year BETWEEN ? AND ? GROUP BY s.year"""
                .formatted(STUDENT, scope), from, last)
                .forEach(r -> add(byYear, r.get("y"), 0, r.get("n")));
        jdbc.queryForList("""
                SELECT YEAR(s.study_status_date) y, s.study_status st, COUNT(*) n FROM users s
                WHERE %s%s AND s.study_status IN ('GRADUATED', 'DROPPED_OUT') AND s.study_status_date >= ? AND s.study_status_date <= ?
                GROUP BY y, st""".formatted(STUDENT, scope), LocalDate.of(from, 1, 1), LocalDate.of(last, 12, 31))
                .forEach(r -> add(byYear, r.get("y"), StudyStatus.GRADUATED.equals(r.get("st")) ? 1 : 2, r.get("n")));
        List<String> labels = new ArrayList<>();
        List<Long> joined = new ArrayList<>(), graduated = new ArrayList<>(), dropped = new ArrayList<>();
        byYear.forEach((y, v) -> { labels.add(String.valueOf(y)); joined.add(v[0]); graduated.add(v[1]); dropped.add(v[2]); });
        return series(labels, List.of(serie("Joined", joined), serie("Graduated", graduated), serie("Dropped out", dropped)));
    }

    /**
     * Students per institution and per course for a choice of students: "now" (studying or on hold), "all" (every
     * student ever), or one session year (all its students, whatever their study status now). Also the years to choose.
     */
    public Map<String, Object> studentEnrolment(String period) {
        String scope = scope("s");
        String p = period == null || period.isBlank() ? "now" : period.trim().toLowerCase(Locale.ROOT);
        String which;
        Object[] args;
        if ("now".equals(p)) { which = IN_PROGRAMME; args = new Object[0]; }
        else if ("all".equals(p)) { which = ""; args = new Object[0]; }
        else {
            int year;
            try { year = Integer.parseInt(p); } catch (NumberFormatException e) { throw new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Choose now, all or a year."); }
            which = " AND s.year = ?";
            args = new Object[]{year};
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", p);
        body.put("years", jdbc.queryForList("SELECT DISTINCT s.year FROM users s WHERE " + STUDENT + " AND s.year IS NOT NULL" + scope
                + " ORDER BY s.year DESC", Integer.class));
        body.put("byInstitution", labelled("""
                SELECT COALESCE(i.institution_name, 'Not assigned') label, COUNT(*) value FROM users s
                LEFT JOIN student_institution_courses sc ON sc.user_id = s.id
                LEFT JOIN institutions i ON i.institution_id = sc.institution_id
                WHERE %s%s%s GROUP BY label ORDER BY value DESC""".formatted(STUDENT, which, scope), args));
        body.put("byCourse", labelled("""
                SELECT COALESCE(co.course_name, 'Not assigned') label, COUNT(*) value FROM users s
                LEFT JOIN student_institution_courses sc ON sc.user_id = s.id
                LEFT JOIN courses co ON co.course_id = sc.course_id
                WHERE %s%s%s GROUP BY label ORDER BY value DESC""".formatted(STUDENT, which, scope), args));
        return body;
    }

    // ================================================================ Sponsors & payments

    public Map<String, Object> sponsors(Integer requestedYear) {
        LocalDate today = LocalDate.now();
        int year = requestedYear == null ? today.getYear() : requestedYear;
        String scope = scope("s");
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        String realSponsor = unassigned == null ? "" : " AND gg.grantor_id <> " + unassigned;

        // Students per sponsor (students still in the programme).
        List<Map<String, Object>> load = jdbc.queryForList("""
                SELECT gg.grantor_id sponsor, COUNT(*) n FROM grantor_grantees gg JOIN users s ON s.id = gg.grantee_id
                WHERE %s%s%s%s GROUP BY gg.grantor_id""".formatted(STUDENT, IN_PROGRAMME, scope, realSponsor));
        long sponsoredStudents = load.stream().mapToLong(r -> num(r.get("n"))).sum();
        long active = activeSponsors();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("activeSponsors", active);
        kpis.put("sponsorsWithStudents", (long) load.size());
        kpis.put("sponsorsWithoutStudents", Math.max(0, active - load.size()));
        kpis.put("sponsoredStudents", sponsoredStudents);
        kpis.put("studentsPerSponsor", load.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(sponsoredStudents).divide(BigDecimal.valueOf(load.size()), 1, RoundingMode.HALF_UP));
        kpis.put("withoutSponsor", withoutSponsor(scope));
        kpis.put("lapsedSponsors", lapsedSponsors(scope, today));
        kpis.put("payThroughRahbar", count("""
                SELECT COUNT(*) FROM users u WHERE u.pays_through_rahbar = 1 AND u.status = 'Active' AND u.merged_into_id IS NULL"""
                + scope("u")));

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("studentsPerSponsor", loadBuckets(load));
        charts.put("sponsorsByChapter", labelled("""
                SELECT COALESCE(c.chapter_name, 'No chapter') label, COUNT(*) value FROM users u
                LEFT JOIN chapters c ON c.chapter_id = u.chapter_id
                WHERE u.status = 'Active' AND u.merged_into_id IS NULL AND %s%s
                GROUP BY label ORDER BY value DESC LIMIT 12""".formatted(isSponsor("u"), scope("u"))));
        // Students sponsored now, by the student's own chapter (pie).
        charts.put("sponsoredByChapter", labelled("""
                SELECT COALESCE(c.chapter_name, 'No chapter') label, COUNT(*) value
                FROM grantor_grantees gg JOIN users s ON s.id = gg.grantee_id LEFT JOIN chapters c ON c.chapter_id = s.chapter_id
                WHERE %s%s%s%s GROUP BY label ORDER BY value DESC""".formatted(STUDENT, IN_PROGRAMME, scope, realSponsor)));
        charts.put("sponsoredByChapterByYear", sponsoredByChapterByYear(scope, unassigned, today.getYear()));
        charts.put("mappings", mappingsByMonth(scope, year));
        // How long the current sponsor-student pairs have been together.
        charts.put("sponsorshipLength", labelled("""
                SELECT CASE WHEN h.started_at >= ? THEN 'Under 1 year' WHEN h.started_at >= ? THEN '1–2 years'
                            WHEN h.started_at >= ? THEN '2–3 years' ELSE '3 years or more' END label,
                       COUNT(*) value, MAX(h.started_at) sort_key
                FROM sponsor_mapping_history h JOIN users s ON s.id = h.grantee_id
                WHERE h.ended_at IS NULL AND h.started_at IS NOT NULL AND %s%s%s GROUP BY label ORDER BY sort_key DESC"""
                .formatted(STUDENT, IN_PROGRAMME, scope), today.minusYears(1).atStartOfDay(), today.minusYears(2).atStartOfDay(),
                today.minusYears(3).atStartOfDay()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("year", year);
        body.put("years", years("SELECT DISTINCT YEAR(payment_date) FROM payments WHERE payment_date IS NOT NULL", today.getYear()));
        body.put("scope", scopeLabel());
        body.put("kpis", kpis);

        if (can(Section.PAYMENT_RECORDS) || can(Section.PAYMENT_DUES)) {
            kpis.put("paidThisYear", paidBetween(scope, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1)));
            kpis.put("paidLastYear", paidLastYear(scope, year, today));
            kpis.putAll(collection(scope, year, today));
            Map<String, Object> overdue = overdue(scope, today);
            kpis.put("overdueInstallments", overdue.get("installments"));
            kpis.put("overdueSponsors", overdue.get("sponsors"));

            // Of the installments that fell due this year and are paid: how many on time, and how late the rest were.
            Map<String, Object> punctual = jdbc.queryForMap("""
                    SELECT COUNT(*) paid, COALESCE(SUM(CASE WHEN DATE(p.payment_date) <= i.due_date THEN 1 ELSE 0 END), 0) on_time,
                           AVG(CASE WHEN DATE(p.payment_date) > i.due_date THEN DATEDIFF(p.payment_date, i.due_date) END) avg_days_late
                    FROM payment_installments i JOIN payments p ON p.payment_id = i.payment_id JOIN users s ON s.id = i.grantee_id
                    WHERE i.not_needed = 0 AND i.due_date >= ? AND i.due_date < ?%s""".formatted(scope), LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1));
            kpis.put("onTimeRate", percent(num(punctual.get("on_time")), num(punctual.get("paid"))));
            kpis.put("avgDaysLate", punctual.get("avg_days_late") == null ? null : Math.round(((Number) punctual.get("avg_days_late")).doubleValue()));

            charts.put("collectedByMonth", collectedByMonth(scope, year));
            charts.put("dueVsPaid", dueVsPaid(scope, year));
            charts.put("overdueAgeing", overdueAgeing(scope, today));
            charts.put("upcoming", upcoming(scope, today));
            charts.put("methods", labelled("""
                    SELECT CASE UPPER(COALESCE(p.method, '')) WHEN '' THEN 'Not recorded' WHEN 'BANK' THEN 'Bank transfer'
                                WHEN 'UPI' THEN 'UPI' WHEN 'CASH' THEN 'Cash' WHEN 'CHEQUE' THEN 'Cheque' ELSE p.method END label,
                           COUNT(*) value
                    FROM payments p JOIN users s ON s.id = p.grantee_id
                    WHERE LOWER(p.status) = 'paid' AND p.payment_date >= ? AND p.payment_date < ?%s
                    GROUP BY label ORDER BY value DESC""".formatted(scope), yearStart(year), yearStart(year + 1)));
            body.put("mostOverdue", jdbc.queryForList("""
                    SELECT sp.user_id, sp.name, COUNT(DISTINCT i.grantee_id) students, COUNT(*) installments,
                           MIN(i.due_date) oldest_due
                    FROM payment_installments i JOIN users sp ON sp.id = i.grantor_id JOIN users s ON s.id = i.grantee_id
                    WHERE i.payment_id IS NULL AND i.not_needed = 0 AND i.due_date < ? AND s.merged_into_id IS NULL%s%s
                    GROUP BY sp.id, sp.user_id, sp.name ORDER BY installments DESC, oldest_due LIMIT 10""".formatted(IN_PROGRAMME, scope), today));
        }
        if (can(Section.ONLINE_PAYMENTS)) {
            charts.put("onlineByPurpose", labelled("""
                    SELECT CASE purpose WHEN 'SPONSOR' THEN 'Sponsors' ELSE 'Donations' END label, COALESCE(SUM(amount), 0) value
                    FROM online_payments WHERE status = 'PAID' AND paid_at >= ? AND paid_at < ?
                    GROUP BY label ORDER BY value DESC""", yearStart(year), yearStart(year + 1)));
        }
        body.put("charts", charts);
        return body;
    }

    /** Chapters drawn as their own line; the rest are added up as "Others". */
    static final int TREND_CHAPTERS = 6;
    /** Years shown at most (the latest ones). */
    static final int TREND_YEARS = 10;

    /**
     * Students sponsored in each year, per chapter (the student's chapter): a student counts in every year in which
     * they had a sponsor at some point (from the mapping history). The chapters with the most students get their own
     * line, the rest are "Others", so a chapter's sponsorship growing or falling shows year by year.
     */
    private Map<String, Object> sponsoredByChapterByYear(String scope, Long unassigned, int thisYear) {
        List<Map<String, Object>> periods = jdbc.queryForList("""
                SELECT h.grantee_id student, COALESCE(c.chapter_name, 'No chapter') chapter, h.started_at, h.ended_at
                FROM sponsor_mapping_history h JOIN users s ON s.id = h.grantee_id LEFT JOIN chapters c ON c.chapter_id = s.chapter_id
                WHERE h.started_at IS NOT NULL AND s.role_id = 6 AND s.merged_into_id IS NULL%s%s"""
                .formatted(scope, unassigned == null ? "" : " AND h.grantor_id <> " + unassigned));
        int first = thisYear;
        for (Map<String, Object> p : periods) first = Math.min(first, toDateTime(p.get("started_at")).getYear());
        first = Math.max(first, thisYear - TREND_YEARS + 1);
        int years = thisYear - first + 1;
        // chapter -> per year: the students sponsored that year
        Map<String, List<Set<Long>>> byChapter = new HashMap<>();
        for (Map<String, Object> p : periods) {
            int from = Math.max(first, toDateTime(p.get("started_at")).getYear());
            int to = p.get("ended_at") == null ? thisYear : Math.min(thisYear, toDateTime(p.get("ended_at")).getYear());
            List<Set<Long>> perYear = byChapter.computeIfAbsent((String) p.get("chapter"), k -> {
                List<Set<Long>> l = new ArrayList<>();
                for (int i = 0; i < years; i++) l.add(new HashSet<>());
                return l;
            });
            for (int y = from; y <= to; y++) perYear.get(y - first).add(num(p.get("student")));
        }
        // the biggest chapters (students over all the years) get a line each
        List<String> chapters = new ArrayList<>(byChapter.keySet());
        chapters.sort(Comparator.comparingLong((String ch) -> byChapter.get(ch).stream().mapToLong(Set::size).sum()).reversed()
                .thenComparing(Comparator.naturalOrder()));
        List<Map<String, Object>> lines = new ArrayList<>();
        long[] others = new long[years];
        for (int i = 0; i < chapters.size(); i++) {
            List<Set<Long>> perYear = byChapter.get(chapters.get(i));
            if (i < TREND_CHAPTERS) {
                lines.add(serie(chapters.get(i), perYear.stream().map(st -> (long) st.size()).toList()));
            } else {
                for (int y = 0; y < years; y++) others[y] += perYear.get(y).size();
            }
        }
        if (chapters.size() > TREND_CHAPTERS) lines.add(serie("Others", toList(others)));
        List<String> labels = new ArrayList<>();
        for (int y = first; y <= thisYear; y++) labels.add(String.valueOf(y));
        return series(labels, lines);
    }

    private static java.time.LocalDateTime toDateTime(Object v) {
        if (v instanceof java.time.LocalDateTime t) return t;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (v instanceof java.time.LocalDate d) return d.atStartOfDay();
        return java.time.LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));
    }

    /** Sponsor-student pairings started and ended per month of the year (from the mapping history). */
    private Map<String, Object> mappingsByMonth(String scope, int year) {
        long[] started = new long[12], ended = new long[12];
        jdbc.queryForList("""
                SELECT MONTH(h.started_at) m, COUNT(*) n FROM sponsor_mapping_history h JOIN users s ON s.id = h.grantee_id
                WHERE h.started_at >= ? AND h.started_at < ?%s GROUP BY m""".formatted(scope), yearStart(year), yearStart(year + 1))
                .forEach(r -> started[(int) num(r.get("m")) - 1] = num(r.get("n")));
        jdbc.queryForList("""
                SELECT MONTH(h.ended_at) m, COUNT(*) n FROM sponsor_mapping_history h JOIN users s ON s.id = h.grantee_id
                WHERE h.ended_at >= ? AND h.ended_at < ?%s GROUP BY m""".formatted(scope), yearStart(year), yearStart(year + 1))
                .forEach(r -> ended[(int) num(r.get("m")) - 1] = num(r.get("n")));
        return series(months(), List.of(serie("Started", toList(started)), serie("Ended", toList(ended))));
    }

    /** Installments falling due in each month of the year: how many were due and how many of them are paid. */
    private Map<String, Object> dueVsPaid(String scope, int year) {
        BigDecimal[] due = zeros(), paid = zeros();
        jdbc.queryForList("""
                SELECT MONTH(i.due_date) m, COUNT(*) due, COUNT(i.payment_id) paid
                FROM payment_installments i JOIN users s ON s.id = i.grantee_id
                WHERE i.not_needed = 0 AND i.due_date >= ? AND i.due_date < ? AND s.merged_into_id IS NULL%s GROUP BY m"""
                .formatted(scope), LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
                .forEach(r -> {
                    int m = (int) num(r.get("m")) - 1;
                    due[m] = money(r.get("due"));
                    paid[m] = money(r.get("paid"));
                });
        return series(months(), List.of(serie("Due", Arrays.asList(due)), serie("Paid", Arrays.asList(paid))));
    }

    /** Unpaid installments due within 7 / 30 / 60 / 90 days (each includes the shorter ones). */
    private List<Map<String, Object>> upcoming(String scope, LocalDate today) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN i.due_date <= ? THEN 1 ELSE 0 END), 0) d7,
                       COALESCE(SUM(CASE WHEN i.due_date <= ? THEN 1 ELSE 0 END), 0) d30,
                       COALESCE(SUM(CASE WHEN i.due_date <= ? THEN 1 ELSE 0 END), 0) d60,
                       COUNT(*) d90
                FROM payment_installments i JOIN users s ON s.id = i.grantee_id
                WHERE i.payment_id IS NULL AND i.not_needed = 0 AND i.due_date >= ? AND i.due_date <= ? AND s.merged_into_id IS NULL%s%s"""
                .formatted(IN_PROGRAMME, scope), today.plusDays(7), today.plusDays(30), today.plusDays(60), today, today.plusDays(90));
        return List.of(point("Within 7 days", r.get("d7")), point("Within 30 days", r.get("d30")),
                point("Within 60 days", r.get("d60")), point("Within 90 days", r.get("d90")));
    }

    // ================================================================ Applications

    public Map<String, Object> applications(Integer requestedYear) {
        int thisYear = LocalDate.now().getYear();
        List<Integer> years = years("SELECT DISTINCT YEAR(created_at) FROM scholarship_applications WHERE created_at IS NOT NULL", thisYear);
        int year = requestedYear == null ? years.get(0) : requestedYear;
        String scope = scope("s");
        LocalDateTime from = yearStart(year), to = yearStart(year + 1);
        String inYear = " AND g.created_at >= ? AND g.created_at < ?";
        String base = APPS + inYear + scope;

        Map<String, Long> status = applicationStatusCounts(scope, year);
        long total = status.values().stream().mapToLong(Long::longValue).sum();
        long open = status.entrySet().stream().filter(e -> OPEN.contains(e.getKey())).mapToLong(Map.Entry::getValue).sum();
        long accepted = status.getOrDefault(ScholarshipApplication.ACCEPTED, 0L);
        long rejected = status.getOrDefault(ScholarshipApplication.REJECTED, 0L);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("received", total);
        kpis.put("applied", status.getOrDefault(ScholarshipApplication.APPLIED, 0L));
        kpis.put("open", open);
        kpis.put("accepted", accepted);
        kpis.put("rejected", rejected);
        kpis.put("acceptanceRate", percent(accepted, accepted + rejected));
        kpis.put("interviewsUpcoming", count("SELECT COUNT(*)" + base + " AND g.interview_at >= ?", from, to, LocalDateTime.now()));
        kpis.put("interviewsWithoutReview", count("SELECT COUNT(*)" + base + " AND g.interview_at < ? AND g.status = 'INTERVIEW'"
                + " AND NOT EXISTS (SELECT 1 FROM interview_reviews r WHERE r.application_id = g.id)",
                from, to, LocalDateTime.now()));
        // Days from applying to the decision (accepted or rejected).
        Object days = jdbc.queryForObject("SELECT AVG(DATEDIFF(g.decided_at, g.created_at))" + base + " AND g.decided_at IS NOT NULL",
                Object.class, from, to);
        kpis.put("daysToDecision", days instanceof Number n ? (Object) Math.round(n.doubleValue()) : null);

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("funnel", funnel(scope, year));
        List<Map<String, Object>> byStatus = new ArrayList<>();
        for (String code : ApplicationService.STATUS_LABELS.keySet()) {
            byStatus.add(point(ApplicationService.label(code), status.getOrDefault(code, 0L)));
        }
        charts.put("byStatus", byStatus);
        long[] perMonth = new long[12];
        jdbc.queryForList("SELECT MONTH(g.created_at) m, COUNT(*) n" + base + " GROUP BY m", from, to)
                .forEach(r -> perMonth[(int) num(r.get("m")) - 1] = num(r.get("n")));
        charts.put("byMonth", series(months(), List.of(serie("Applications", toList(perMonth)))));
        charts.put("byCourse", labelled("SELECT COALESCE(NULLIF(TRIM(g.course_applied), ''), 'Not given') label, COUNT(*) value"
                + base + " GROUP BY label ORDER BY value DESC LIMIT 10", from, to));
        charts.put("byRcc", labelled("SELECT COALESCE(NULLIF(TRIM(g.rcc_name), ''), 'Not given') label, COUNT(*) value"
                + base + " GROUP BY label ORDER BY value DESC LIMIT 10", from, to));
        charts.put("bySex", labelled("SELECT CASE g.sex WHEN 'F' THEN 'Female' WHEN 'M' THEN 'Male' ELSE 'Not given' END label, COUNT(*) value"
                + base + " GROUP BY label ORDER BY value DESC", from, to));
        charts.put("familyIncome", labelled("""
                SELECT CASE WHEN g.average_annual_salary IS NULL THEN 'Not given'
                            WHEN g.average_annual_salary < 100000 THEN 'Under ₹1 lakh'
                            WHEN g.average_annual_salary < 200000 THEN '₹1–2 lakh'
                            WHEN g.average_annual_salary < 300000 THEN '₹2–3 lakh'
                            WHEN g.average_annual_salary < 500000 THEN '₹3–5 lakh'
                            ELSE '₹5 lakh or more' END label, COUNT(*) value, MIN(COALESCE(g.average_annual_salary, 99999999)) sort_key"""
                + base + " GROUP BY label ORDER BY sort_key", from, to));
        charts.put("recommendations", labelled("""
                SELECT COALESCE(NULLIF(r.recommendation, ''), 'None') label, COUNT(*) value FROM interview_reviews r
                JOIN scholarship_applications g ON g.id = r.application_id LEFT JOIN users s ON s.id = g.student_user_id
                WHERE 1 = 1""" + inYear + scope + " GROUP BY label ORDER BY value DESC", from, to));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("year", year);
        body.put("years", years);
        body.put("scope", scopeLabel());
        body.put("period", period());
        body.put("kpis", kpis);
        body.put("charts", charts);
        List<Map<String, Object>> upcoming = jdbc.queryForList("SELECT g.id, g.name, g.interview_at, g.interview_venue, g.status"
                + APPS + scope + " AND g.interview_at >= ? ORDER BY g.interview_at LIMIT 10", LocalDateTime.now());
        upcoming.forEach(r -> r.put("status", ApplicationService.label(String.valueOf(r.get("status")))));
        body.put("upcomingInterviews", upcoming);
        return body;
    }

    /** Applied → reviewed (moved on from Applied, incl. rejected) → interview → accepted, for one year. */
    private List<Map<String, Object>> funnel(String scope, int year) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COUNT(*) received,
                       COALESCE(SUM(CASE WHEN g.status <> 'APPLIED' THEN 1 ELSE 0 END), 0) in_process,
                       COALESCE(SUM(CASE WHEN g.interview_at IS NOT NULL OR g.status IN ('INTERVIEW', 'ACCEPTED') THEN 1 ELSE 0 END), 0) interviewed,
                       COALESCE(SUM(CASE WHEN g.status = 'ACCEPTED' THEN 1 ELSE 0 END), 0) accepted
                """ + APPS + " AND g.created_at >= ? AND g.created_at < ?" + scope, yearStart(year), yearStart(year + 1));
        return List.of(point("Applied", r.get("received")), point("Reviewed", r.get("in_process")),
                point("Interview", r.get("interviewed")), point("Accepted", r.get("accepted")));
    }

    /** Number of applications per status code (all years when {@code year} is null). */
    private Map<String, Long> applicationStatusCounts(String scope, Integer year) {
        String sql = "SELECT g.status st, COUNT(*) n" + APPS + (year == null ? "" : " AND g.created_at >= ? AND g.created_at < ?")
                + scope + " GROUP BY g.status";
        List<Map<String, Object>> rows = year == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, yearStart(year), yearStart(year + 1));
        Map<String, Long> counts = new LinkedHashMap<>();
        rows.forEach(r -> counts.put(String.valueOf(r.get("st")), num(r.get("n"))));
        return counts;
    }

    // ================================================================ shared parts

    private Map<String, Long> studyStatusCounts(String scope) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.queryForList("SELECT " + STUDY + " st, COUNT(*) n FROM users s WHERE " + STUDENT + scope + " GROUP BY st")
                .forEach(r -> counts.put(String.valueOf(r.get("st")), num(r.get("n"))));
        return counts;
    }

    private static List<Map<String, Object>> studyStatusChart(Map<String, Long> counts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String code : StudyStatus.LABELS.keySet()) rows.add(point(StudyStatus.label(code), counts.getOrDefault(code, 0L)));
        return rows;
    }

    /** Active sponsors and convenors (of the user's chapter / RCC center when their role covers only that). */
    private long activeSponsors() {
        return count("SELECT COUNT(*) FROM users u WHERE u.status = 'Active' AND u.merged_into_id IS NULL AND " + isSponsor("u") + scope("u"));
    }

    /** Sponsor or convenor as main or additional role. */
    private static String isSponsor(String alias) {
        return "(" + alias + ".role_id IN (" + SPONSOR_ROLE_IDS + ") OR EXISTS (SELECT 1 FROM user_additional_roles r WHERE r.user_id = "
                + alias + ".id AND r.role_id IN (" + SPONSOR_ROLE_IDS + ")))";
    }

    private long sponsorsWithStudents(String scope) {
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        return count("SELECT COUNT(DISTINCT gg.grantor_id) FROM grantor_grantees gg JOIN users s ON s.id = gg.grantee_id WHERE "
                + STUDENT + IN_PROGRAMME + scope + (unassigned == null ? "" : " AND gg.grantor_id <> " + unassigned));
    }

    private long withoutSponsor(String scope) {
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        return count("SELECT COUNT(*) FROM users s WHERE " + STUDENT + " AND s.status = 'Active' AND " + STUDY + " = 'STUDYING'" + scope
                + " AND NOT EXISTS (SELECT 1 FROM grantor_grantees gg WHERE gg.grantee_id = s.id"
                + (unassigned == null ? "" : " AND gg.grantor_id <> " + unassigned) + ")");
    }

    private long lapsedSponsors(String scope, LocalDate today) {
        return count("""
                SELECT COUNT(DISTINCT i.grantor_id) FROM payment_installments i JOIN users s ON s.id = i.grantee_id
                WHERE i.payment_id IS NULL AND i.not_needed = 0 AND i.due_date < ? AND s.merged_into_id IS NULL%s%s""".formatted(IN_PROGRAMME, scope),
                today.minusDays(30));
    }

    private Map<String, Object> overdue(String scope, LocalDate today) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) installments, COUNT(DISTINCT i.grantee_id) students, COUNT(DISTINCT i.grantor_id) sponsors
                FROM payment_installments i JOIN users s ON s.id = i.grantee_id
                WHERE i.payment_id IS NULL AND i.not_needed = 0 AND i.due_date < ? AND s.merged_into_id IS NULL%s%s""".formatted(IN_PROGRAMME, scope), today);
    }

    /** How many of the installments that fell due this year (up to today) are paid. */
    private Map<String, Object> collection(String scope, int year, LocalDate today) {
        LocalDate end = year < today.getYear() ? LocalDate.of(year, 12, 31) : today;
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COUNT(*) due, COUNT(i.payment_id) paid
                FROM payment_installments i JOIN users s ON s.id = i.grantee_id
                WHERE i.not_needed = 0 AND i.due_date >= ? AND i.due_date <= ? AND s.merged_into_id IS NULL%s""".formatted(scope),
                LocalDate.of(year, 1, 1), end);
        BigDecimal due = money(r.get("due")), paid = money(r.get("paid"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dueSoFar", due);
        out.put("paidOfDue", paid);
        out.put("collectionRate", due.signum() == 0 ? null : paid.multiply(BigDecimal.valueOf(100)).divide(due, 0, RoundingMode.HALF_UP));
        return out;
    }

    /** Installments paid (paid payments) from {@code from} up to (not including) {@code to}. Amounts are never shown. */
    private BigDecimal paidBetween(String scope, LocalDate from, LocalDate to) {
        return money(jdbc.queryForObject("""
                SELECT COUNT(*) FROM payments p JOIN users s ON s.id = p.grantee_id
                WHERE LOWER(p.status) = 'paid' AND p.payment_date >= ? AND p.payment_date < ?%s""".formatted(scope),
                BigDecimal.class, from.atStartOfDay(), to.atStartOfDay()));
    }

    /**
     * The year before, to compare with: up to the same day last year while the chosen year is still running
     * (otherwise every year would look worse than the last until December), the whole year for past years.
     */
    private BigDecimal paidLastYear(String scope, int year, LocalDate today) {
        LocalDate end = year == today.getYear() ? today.minusYears(1).plusDays(1) : LocalDate.of(year, 1, 1);
        return paidBetween(scope, LocalDate.of(year - 1, 1, 1), end);
    }

    /** Installments paid per month: the chosen year next to the year before. */
    private Map<String, Object> collectedByMonth(String scope, int year) {
        BigDecimal[] current = zeros(), previous = zeros();
        jdbc.queryForList("""
                SELECT YEAR(p.payment_date) y, MONTH(p.payment_date) m, COUNT(*) amount
                FROM payments p JOIN users s ON s.id = p.grantee_id
                WHERE LOWER(p.status) = 'paid' AND p.payment_date >= ? AND p.payment_date < ?%s GROUP BY y, m""".formatted(scope),
                yearStart(year - 1), yearStart(year + 1))
                .forEach(r -> ((int) num(r.get("y")) == year ? current : previous)[(int) num(r.get("m")) - 1] = money(r.get("amount")));
        return series(months(), List.of(serie(String.valueOf(year - 1), Arrays.asList(previous)), serie(String.valueOf(year), Arrays.asList(current))));
    }

    /** Overdue installments by how late they are. */
    private List<Map<String, Object>> overdueAgeing(String scope, LocalDate today) {
        Map<String, Object> r = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN i.due_date >= ? THEN 1 ELSE 0 END), 0) b1,
                       COALESCE(SUM(CASE WHEN i.due_date < ? AND i.due_date >= ? THEN 1 ELSE 0 END), 0) b2,
                       COALESCE(SUM(CASE WHEN i.due_date < ? AND i.due_date >= ? THEN 1 ELSE 0 END), 0) b3,
                       COALESCE(SUM(CASE WHEN i.due_date < ? THEN 1 ELSE 0 END), 0) b4
                FROM payment_installments i JOIN users s ON s.id = i.grantee_id
                WHERE i.payment_id IS NULL AND i.not_needed = 0 AND i.due_date < ? AND s.merged_into_id IS NULL%s%s""".formatted(IN_PROGRAMME, scope),
                today.minusDays(30), today.minusDays(30), today.minusDays(90), today.minusDays(90), today.minusDays(180),
                today.minusDays(180), today);
        return List.of(point("Up to 30 days", r.get("b1")), point("31–90 days", r.get("b2")),
                point("91–180 days", r.get("b3")), point("Over 180 days", r.get("b4")));
    }


    private Map<String, Object> reb(LocalDate today) {
        Map<String, Object> reb = new LinkedHashMap<>();
        reb.put("activeGirls", count("SELECT COUNT(*) FROM reb_students WHERE status = 'ACTIVE'"));
        Map<String, Object> dues = jdbc.queryForMap("""
                SELECT COUNT(*) n, COALESCE(SUM(net - sponsor_paid - family_paid), 0) amount FROM reb_invoices
                WHERE status IN ('DUE', 'PARTIAL')""");
        reb.put("openInvoices", dues.get("n"));
        reb.put("openAmount", dues.get("amount"));
        Map<String, Object> att = jdbc.queryForMap("""
                SELECT COUNT(*) marked, COALESCE(SUM(CASE WHEN status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END), 0) present
                FROM reb_attendance WHERE date = ?""", today);
        reb.put("attendanceMarked", att.get("marked"));
        reb.put("attendanceToday", percent(num(att.get("present")), num(att.get("marked"))));
        return reb;
    }

    private Map<String, Object> period() {
        return periods.findById(1L).map(Rows::of).orElse(null);
    }

    /** 1, 2, 3, 4 and 5+ students per sponsor: how many sponsors have that many. */
    static List<Map<String, Object>> loadBuckets(List<Map<String, Object>> perSponsor) {
        long[] buckets = new long[5];
        for (Map<String, Object> r : perSponsor) buckets[(int) Math.min(5, Math.max(1, num(r.get("n")))) - 1]++;
        return List.of(point("1 student", buckets[0]), point("2 students", buckets[1]), point("3 students", buckets[2]),
                point("4 students", buckets[3]), point("5 or more", buckets[4]));
    }

    // ---------------------------------------------------------------- scope and permissions

    /** The session year the dashboards are filtered by while one request runs ("2025", "none" or null for all). */
    private static final ThreadLocal<String> SESSION_YEAR = new ThreadLocal<>();

    /** Runs a dashboard for the students of one session year ("2025"; "none": without one; empty: all). */
    public <T> T forSessionYear(String sessionYear, java.util.function.Supplier<T> dashboard) {
        SESSION_YEAR.set(sessionYear == null || sessionYear.isBlank() ? null : sessionYear.trim());
        try {
            return dashboard.get();
        } finally {
            SESSION_YEAR.remove();
        }
    }

    /**
     * Restricts student rows (alias "s") to the user's chapter / RCC center when their role only covers that, and to
     * the chosen session year. Other aliases (sponsors, "u") only get the chapter / RCC part.
     */
    String scope(String alias) {
        return placeScope(alias) + ("s".equals(alias) ? sessionYearSql(alias, SESSION_YEAR.get()) : "");
    }

    /** " AND s.year = 2025" / " AND s.year IS NULL" / "" (the year is checked to be a number: safe in SQL). */
    static String sessionYearSql(String alias, String sessionYear) {
        if (sessionYear == null || sessionYear.isBlank()) return "";
        if ("none".equalsIgnoreCase(sessionYear)) return " AND " + alias + ".year IS NULL";
        if (!sessionYear.matches("\\d{4}")) throw new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Session year must be a year like 2025.");
        return " AND " + alias + ".year = " + Integer.parseInt(sessionYear);
    }

    /** Adds session_years (the students' session years the user may see, newest first) for the dashboard's dropdown. */
    public Map<String, Object> withSessionYears(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>(body);
        out.put("session_years", jdbc.queryForList("SELECT DISTINCT s.year FROM users s WHERE " + STUDENT + " AND s.year IS NOT NULL"
                + placeScope("s") + " ORDER BY s.year DESC", Integer.class));
        return out;
    }

    /** Only the chapter / RCC centre part (applications aren't filtered by session year). */
    String placeScope(String alias) {
        User u = Access.current().getUser();
        if (Access.chapterScoped()) return " AND " + alias + ".chapter_id = " + (u.getChapterId() == null ? "-1" : u.getChapterId());
        if (Access.rccScoped()) return " AND " + alias + ".rcc_center_id = " + (u.getRccCenterId() == null ? "-1" : u.getRccCenterId());
        return "";
    }

    private static String scopeLabel() {
        if (Access.chapterScoped()) return "chapter";
        if (Access.rccScoped()) return "rcc";
        return "all";
    }

    private static boolean can(Section section) {
        return Access.can(section, Section.Level.VIEW);
    }

    // ---------------------------------------------------------------- small helpers

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private List<Map<String, Object>> labelled(String sql, Object... args) {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.queryForList(sql, args).forEach(r -> rows.add(point(String.valueOf(r.get("label")), r.get("value"))));
        return rows;
    }

    /** Distinct years from the query, always including this year, newest first. */
    private List<Integer> years(String sql, int thisYear) {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(thisYear);
        jdbc.queryForList(sql, Integer.class).forEach(y -> { if (y != null) years.add(y); });
        return new ArrayList<>(years);
    }

    private static Map<String, Object> item(String key, String label, long count, BigDecimal amount, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("count", count);
        if (amount != null) m.put("amount", amount);
        m.put("link", link);
        return m;
    }

    static Map<String, Object> point(String label, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value instanceof BigDecimal || value instanceof Long ? value
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

    private static List<String> months() {
        return List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] a = new BigDecimal[12];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static List<Long> toList(long[] values) {
        List<Long> list = new ArrayList<>();
        for (long v : values) list.add(v);
        return list;
    }

    private static void add(Map<Integer, long[]> byYear, Object year, int index, Object n) {
        long[] v = year == null ? null : byYear.get((int) num(year));
        if (v != null) v[index] += num(n);
    }

    static Integer percent(long part, long whole) {
        return whole == 0 ? null : (int) Math.round(part * 100.0 / whole);
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static BigDecimal money(Object o) {
        if (o instanceof BigDecimal b) return b;
        return o instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }

    private static LocalDateTime yearStart(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay();
    }
}
