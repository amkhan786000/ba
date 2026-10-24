package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Students > Early Warning: scholarship and coaching students showing signs they may drop out, and the follow-up
 * calls / visits made about them.
 * <ul>
 *   <li>Scholarship students (studying or on hold): latest progress marks under 50% or down 10+ points; the latest
 *   progress report not sent; an installment unpaid 30+ days after its due date; study status On hold.</li>
 *   <li>Coaching students: last exam under 40% or down 15+ points; missed the last 2 exams of their class; attendance
 *   under 75% in the last 30 days (with at least 5 days marked).</li>
 *   <li>Either: flagged, and no follow-up recorded in 60 days.</li>
 * </ul>
 * Chapter leads see their chapter's scholarship students, coaching center coordinators their center's students.
 */
@Service
public class EarlyWarningService {

    public static final String SCHOLAR = "SCHOLAR", COACHING = "COACHING";
    static final double SCHOLAR_LOW = 50, SCHOLAR_DROP = 10, COACHING_LOW = 40, COACHING_DROP = 15, ATTENDANCE_LOW = 75;
    static final int INSTALLMENT_LATE_DAYS = 30, NO_CONTACT_DAYS = 60, RECENT_DAYS = 14, ATTENDANCE_MIN_DAYS = 5;
    static final Set<String> METHODS = Set.of("CALL", "VISIT", "MEETING", "OTHER");
    static final Set<String> OUTCOMES = Set.of("RESOLVED", "MONITOR", "ESCALATE");

    /** A warning sign: a code, what the screen shows, and the detail (e.g. "42%"). */
    public record Signal(String code, String label, String detail) {}

    private final UserRepository users;
    private final StudentProgressRepository progress;
    private final PaymentInstallmentRepository installments;
    private final StudentReminderService reminders;
    private final StudentScope studentScope;
    private final CoachingStudentRepository coachingStudents;
    private final CoachingAccess coachingAccess;
    private final CoachingResults coachingResults;
    private final CoachingAttendanceService attendance;
    private final StudentFollowupRepository followups;

    public EarlyWarningService(UserRepository users, StudentProgressRepository progress, PaymentInstallmentRepository installments,
                               StudentReminderService reminders, StudentScope studentScope, CoachingStudentRepository coachingStudents,
                               CoachingAccess coachingAccess, CoachingResults coachingResults, CoachingAttendanceService attendance,
                               StudentFollowupRepository followups) {
        this.users = users;
        this.progress = progress;
        this.installments = installments;
        this.reminders = reminders;
        this.studentScope = studentScope;
        this.coachingStudents = coachingStudents;
        this.coachingAccess = coachingAccess;
        this.coachingResults = coachingResults;
        this.attendance = attendance;
        this.followups = followups;
    }

    /**
     * The flagged students the user may see, most signs first. view: NEEDS (not followed up in the last 14 days, or
     * their next follow-up date has come; default), FOLLOWED (followed up recently) or ALL. kind: SCHOLAR, COACHING
     * or empty for both.
     */
    public Map<String, Object> list(String kind, String view, String signal, String q) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (kind == null || kind.isBlank() || SCHOLAR.equalsIgnoreCase(kind)) rows.addAll(scholars(today));
        if (kind == null || kind.isBlank() || COACHING.equalsIgnoreCase(kind)) rows.addAll(coaching(today));

        Map<String, Integer> bySignal = new TreeMap<>();
        int needs = 0, followed = 0;
        for (Map<String, Object> r : rows) {
            for (Signal s : signals(r)) bySignal.merge(s.label(), 1, Integer::sum);
            if (Boolean.TRUE.equals(r.get("needs_follow_up"))) needs++; else followed++;
        }
        String v = view == null || view.isBlank() ? "NEEDS" : view.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> shown = rows.stream()
                .filter(r -> "ALL".equals(v) || ("NEEDS".equals(v) == Boolean.TRUE.equals(r.get("needs_follow_up"))))
                .filter(r -> signal == null || signal.isBlank() || signals(r).stream().anyMatch(s -> s.code().equalsIgnoreCase(signal)))
                .filter(r -> com.rahbar.util.Paging.matches(q, r.get("name"), r.get("code"), r.get("place")))
                .sorted(Comparator.comparing((Map<String, Object> r) -> signals(r).size()).reversed()
                        .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", shown);
        out.put("needs_follow_up", needs);
        out.put("followed_up", followed);
        out.put("by_signal", bySignal);
        out.put("coaching", !coachingAccess.centerIds().isEmpty());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Signal> signals(Map<String, Object> r) {
        return (List<Signal>) r.get("signals");
    }

    // ---------------------------------------------------------------- scholarship students

    private List<Map<String, Object>> scholars(LocalDate today) {
        StudentScope.Scope scope = studentScope.current();
        if (scope.none()) return List.of();
        List<User> list = users.findByRoleId(ServiceSupport.STUDENT_ROLE).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null)
                .filter(u -> StudyStatus.inProgramme(u.getStudyStatus()) && !DataQualityService.coachingOnly(u))
                .filter(scope::includes)
                .toList();
        if (list.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        list.forEach(u -> ids.add(u.getId()));
        Map<Long, List<StudentProgress>> reports = new HashMap<>();
        for (StudentProgress p : progress.findAll()) {
            if (ids.contains(p.getGranteeId()) && !"Rejected".equalsIgnoreCase(p.getReviewStatus()) && ServiceSupport.marks(p.getMarks()) != null) {
                reports.computeIfAbsent(p.getGranteeId(), k -> new ArrayList<>()).add(p);
            }
        }
        reports.values().forEach(l -> l.sort(Comparator.comparing(StudentProgress::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))));
        Map<Long, LocalDate> lateInstallment = new HashMap<>();
        for (PaymentInstallment i : installments.findByGranteeIdInOrderByGranteeIdAscInstallmentNoAsc(ids)) {
            if (i.getPaymentId() == null && i.getDueDate() != null && i.getDueDate().isBefore(today.minusDays(INSTALLMENT_LATE_DAYS))) {
                lateInstallment.merge(i.getGranteeId(), i.getDueDate(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        Map<Long, String> overdueReports = reminders.overdueReports(list);
        Map<Long, StudentFollowup> last = latest(SCHOLAR);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : list) {
            List<Signal> signals = new ArrayList<>();
            List<StudentProgress> r = reports.getOrDefault(u.getId(), List.of());
            if (!r.isEmpty()) {
                double latest = ServiceSupport.marks(r.get(r.size() - 1).getMarks());
                if (latest < SCHOLAR_LOW) signals.add(new Signal("LOW_MARKS", "Low marks", fmt(latest) + "% in the latest report"));
                if (r.size() > 1) {
                    double before = ServiceSupport.marks(r.get(r.size() - 2).getMarks());
                    if (before - latest >= SCHOLAR_DROP) signals.add(new Signal("FALLING_MARKS", "Marks falling", fmt(before) + "% → " + fmt(latest) + "%"));
                }
            }
            if (overdueReports.containsKey(u.getId())) signals.add(new Signal("REPORT_OVERDUE", "Progress report not sent", overdueReports.get(u.getId())));
            if (lateInstallment.containsKey(u.getId())) signals.add(new Signal("INSTALLMENT_OVERDUE", "Installment overdue", "unpaid since " + lateInstallment.get(u.getId())));
            if (StudyStatus.ON_HOLD.equals(StudyStatus.of(u.getStudyStatus()))) signals.add(new Signal("ON_HOLD", "Studies on hold", u.getStudyStatusNote()));
            Map<String, Object> row = row(SCHOLAR, u.getId(), u.getName(), u.getUserId(), u.getChapterName(),
                    u.getYear() == null ? "Scholarship" : "Scholarship " + u.getYear(), u.getPhone(), signals, last.get(u.getId()), today);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    // ---------------------------------------------------------------- coaching students

    private List<Map<String, Object>> coaching(LocalDate today) {
        Set<Long> centers = coachingAccess.centerIds();
        if (centers.isEmpty()) return List.of();
        Map<Long, String> centerNames = coachingAccess.names();
        List<CoachingStudent> list = coachingStudents.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) && centers.contains(s.getRccCenterId())).toList();
        if (list.isEmpty()) return List.of();
        Map<Long, CoachingResults.StudentClass> classes = new HashMap<>();
        list.forEach(s -> classes.put(s.getId(), new CoachingResults.StudentClass(s.getRccCenterId(), s.getClassLevel(), s.getAdmissionDate())));
        Map<Long, List<CoachingResults.ExamResult>> res = coachingResults.of(classes);
        Map<Long, long[]> att = attendance.counts(list.stream().map(CoachingStudent::getId).toList(), today.minusDays(29), today);
        Map<Long, StudentFollowup> last = latest(COACHING);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingStudent s : list) {
            List<Signal> signals = new ArrayList<>();
            List<CoachingResults.ExamResult> r = res.getOrDefault(s.getId(), List.of()); // newest first
            List<BigDecimal> sat = r.stream().map(CoachingResults.ExamResult::percent).filter(Objects::nonNull).toList();
            if (!sat.isEmpty()) {
                double latest = sat.get(0).doubleValue();
                if (latest < COACHING_LOW) signals.add(new Signal("LOW_MARKS", "Low marks", fmt(latest) + "% in the last exam"));
                if (sat.size() > 1 && sat.get(1).doubleValue() - latest >= COACHING_DROP) {
                    signals.add(new Signal("FALLING_MARKS", "Marks falling", fmt(sat.get(1).doubleValue()) + "% → " + fmt(latest) + "%"));
                }
            }
            if (r.size() >= 2 && r.get(0).percent() == null && r.get(1).percent() == null) {
                signals.add(new Signal("MISSED_EXAMS", "Missed exams", "absent from the last 2 exams"));
            }
            long[] a = att.getOrDefault(s.getId(), new long[3]);
            BigDecimal pct = CoachingAttendanceService.percent(a[0], a[1]);
            if (pct != null && a[0] + a[1] >= ATTENDANCE_MIN_DAYS && pct.doubleValue() < ATTENDANCE_LOW) {
                signals.add(new Signal("LOW_ATTENDANCE", "Low attendance", pct + "% in the last 30 days"));
            }
            Map<String, Object> row = row(COACHING, s.getId(), s.getName(), s.getAdmissionNo(), centerNames.get(s.getRccCenterId()),
                    "Coaching class " + s.getClassLevel(), s.getGuardianMobile() != null ? s.getGuardianMobile() : s.getStudentMobile(),
                    signals, last.get(s.getId()), today);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    // ---------------------------------------------------------------- rows

    /** A flagged student's row (null when nothing is wrong), with their last follow-up. */
    private static Map<String, Object> row(String kind, Long id, String name, String code, String place, String programme, String phone,
                                           List<Signal> signals, StudentFollowup last, LocalDate today) {
        if (signals.isEmpty()) return null;
        if (last == null || last.getFollowupDate().isBefore(today.minusDays(NO_CONTACT_DAYS))) {
            signals.add(new Signal("NO_CONTACT", "No follow-up", last == null ? "never followed up" : "last on " + last.getFollowupDate()));
        }
        boolean recent = last != null && !last.getFollowupDate().isBefore(today.minusDays(RECENT_DAYS));
        boolean nextDue = last != null && last.getNextDate() != null && !last.getNextDate().isAfter(today);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kind", kind);
        r.put("id", id);
        r.put("name", name);
        r.put("code", code);
        r.put("place", place);
        r.put("programme", programme);
        r.put("phone", phone);
        r.put("signals", signals);
        r.put("last_follow_up", last == null ? null : Rows.pick(last, "followup_date", "method", "outcome", "note", "next_date"));
        r.put("needs_follow_up", !recent || nextDue);
        return r;
    }

    private Map<Long, StudentFollowup> latest(String kind) {
        Map<Long, StudentFollowup> out = new HashMap<>();
        for (StudentFollowup f : followups.findByKindOrderByFollowupDateDescIdDesc(kind)) out.putIfAbsent(f.getStudentId(), f);
        return out;
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- follow-ups

    /** A student's follow-ups, newest first. */
    public List<Map<String, Object>> history(String kind, Long studentId) {
        String k = requireVisible(kind, studentId);
        return Rows.list(followups.findByKindAndStudentIdOrderByFollowupDateDescIdDesc(k, studentId));
    }

    /** Records a follow-up: { kind, studentId, date, method, outcome, note, nextDate }. */
    @Transactional
    public Map<String, Object> record(Map<String, Object> b) {
        Long studentId = ServiceSupport.toLong(b.get("studentId"));
        String kind = requireVisible(String.valueOf(b.get("kind")), studentId);
        StudentFollowup f = new StudentFollowup();
        f.setKind(kind);
        f.setStudentId(studentId);
        LocalDate date = CoachingService.date(b.get("date"), "Date");
        f.setFollowupDate(date == null ? LocalDate.now() : date);
        if (f.getFollowupDate().isAfter(LocalDate.now())) throw CoachingService.bad("The follow-up date can't be in the future.");
        f.setMethod(code(b.get("method"), METHODS, "How you followed up"));
        f.setOutcome(code(b.get("outcome"), OUTCOMES, "Outcome"));
        f.setNote(CoachingService.text(b.get("note"), "Note", 1000, false));
        f.setNextDate(CoachingService.date(b.get("nextDate"), "Next follow-up"));
        if (f.getNextDate() != null && f.getNextDate().isBefore(f.getFollowupDate())) throw CoachingService.bad("The next follow-up must be after this one.");
        followups.save(f);
        return Map.of("message", "Follow-up recorded.");
    }

    /** 404 unless the user may see this student; returns the kind (SCHOLAR / COACHING). */
    private String requireVisible(String kind, Long studentId) {
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (studentId == null) throw CoachingService.bad("Choose the student.");
        if (SCHOLAR.equals(k)) {
            User u = users.findById(studentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found."));
            if (!studentScope.current().includes(u)) throw new ApiException(HttpStatus.NOT_FOUND, "Student not found.");
        } else if (COACHING.equals(k)) {
            CoachingStudent s = coachingStudents.findById(studentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found."));
            coachingAccess.requireVisible(s.getRccCenterId(), "Student");
        } else {
            throw CoachingService.bad("Unknown kind of student.");
        }
        return k;
    }

    private static String code(Object v, Set<String> allowed, String label) {
        String c = v == null ? "" : String.valueOf(v).trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(c)) throw CoachingService.bad(label + ": choose one from the list.");
        return c;
    }
}
