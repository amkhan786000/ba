package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** Early warning: which signs flag a student, and who still needs a follow-up. */
class EarlyWarningServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StudentProgressRepository progress = mock(StudentProgressRepository.class);
    private final PaymentInstallmentRepository installments = mock(PaymentInstallmentRepository.class);
    private final StudentReminderService reminders = mock(StudentReminderService.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final CoachingStudentRepository coachingStudents = mock(CoachingStudentRepository.class);
    private final CoachingAccess access = mock(CoachingAccess.class);
    private final CoachingResults results = mock(CoachingResults.class);
    private final CoachingAttendanceService attendance = mock(CoachingAttendanceService.class);
    private final StudentFollowupRepository followups = mock(StudentFollowupRepository.class);
    private final EarlyWarningService service = new EarlyWarningService(users, progress, installments, reminders, scope, coachingStudents,
            access, results, attendance, followups);
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        when(access.centerIds()).thenReturn(Set.of(10L));
        when(access.names()).thenReturn(Map.of(10L, "Patna"));
    }

    private User scholar(long id, String studyStatus) {
        User u = new User();
        u.setId(id);
        u.setUserId("S" + id);
        u.setName("Scholar " + id);
        u.setRoleId(6);
        u.setStatus("active");
        u.setStudyStatus(studyStatus);
        return u;
    }

    private static StudentProgress report(long student, String marks, int daysAgo) {
        StudentProgress p = new StudentProgress();
        p.setGranteeId(student);
        p.setMarks(marks);
        p.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> codes(Map<String, Object> row) {
        Set<String> out = new HashSet<>();
        ((List<EarlyWarningService.Signal>) row.get("signals")).forEach(s -> out.add(s.code()));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Map<String, Object>> rows(String kind) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        ((List<Map<String, Object>>) service.list(kind, "ALL", null, null).get("rows")).forEach(r -> out.put((Long) r.get("id"), r));
        return out;
    }

    @Test
    void scholarshipSigns() {
        User falling = scholar(1, "STUDYING"), fine = scholar(2, "STUDYING"), onHold = scholar(3, "ON_HOLD"), late = scholar(4, null);
        when(users.findByRoleId(6)).thenReturn(List.of(falling, fine, onHold, late));
        when(progress.findAll()).thenReturn(List.of(report(1, "72", 200), report(1, "55", 10), report(2, "80", 10)));
        PaymentInstallment i = new PaymentInstallment();
        i.setGranteeId(4L);
        i.setDueDate(today.minusDays(45));
        when(installments.findByGranteeIdInOrderByGranteeIdAscInstallmentNoAsc(anyCollection())).thenReturn(List.of(i));
        when(reminders.overdueReports(anyCollection())).thenReturn(Map.of(4L, "Semester 1 (due 2026-09-30)"));

        Map<Long, Map<String, Object>> r = rows("SCHOLAR");
        assertEquals(Set.of("FALLING_MARKS", "NO_CONTACT"), codes(r.get(1L)));
        assertFalse(r.containsKey(2L));
        assertEquals(Set.of("ON_HOLD", "NO_CONTACT"), codes(r.get(3L)));
        assertEquals(Set.of("REPORT_OVERDUE", "INSTALLMENT_OVERDUE", "NO_CONTACT"), codes(r.get(4L)));
    }

    @Test
    void coachingSigns() {
        CoachingStudent weak = new CoachingStudent(), absent = new CoachingStudent(), away = new CoachingStudent();
        long id = 20;
        for (CoachingStudent s : List.of(weak, absent, away)) {
            s.setId(id++);
            s.setName("C" + s.getId());
            s.setRccCenterId(10L);
            s.setClassLevel(9);
        }
        when(coachingStudents.findAll()).thenReturn(List.of(weak, absent, away));
        when(results.of(any())).thenReturn(Map.of(
                20L, List.of(new CoachingResults.ExamResult(1L, today, new BigDecimal("35")), new CoachingResults.ExamResult(2L, today.minusMonths(1), new BigDecimal("60"))),
                21L, List.of(new CoachingResults.ExamResult(1L, today, null), new CoachingResults.ExamResult(2L, today.minusMonths(1), null)),
                22L, List.of()));
        when(attendance.counts(anyCollection(), any(), any())).thenReturn(Map.of(22L, new long[]{3, 7, 0}));

        Map<Long, Map<String, Object>> r = rows("COACHING");
        assertEquals(Set.of("LOW_MARKS", "FALLING_MARKS", "NO_CONTACT"), codes(r.get(20L)));
        assertEquals(Set.of("MISSED_EXAMS", "NO_CONTACT"), codes(r.get(21L)));
        assertEquals(Set.of("LOW_ATTENDANCE", "NO_CONTACT"), codes(r.get(22L)));
    }

    @Test
    void aRecentFollowUpTakesThemOffTheNeedsListUntilTheNextDate() {
        when(users.findByRoleId(6)).thenReturn(List.of(scholar(3, "ON_HOLD"), scholar(5, "ON_HOLD")));
        StudentFollowup recent = new StudentFollowup();
        recent.setStudentId(3L);
        recent.setFollowupDate(today.minusDays(3));
        StudentFollowup due = new StudentFollowup();
        due.setStudentId(5L);
        due.setFollowupDate(today.minusDays(3));
        due.setNextDate(today);
        when(followups.findByKindOrderByFollowupDateDescIdDesc("SCHOLAR")).thenReturn(List.of(recent, due));

        Map<Long, Map<String, Object>> r = rows("SCHOLAR");
        assertEquals(false, r.get(3L).get("needs_follow_up"));
        assertEquals(true, r.get(5L).get("needs_follow_up"));
        assertFalse(codes(r.get(3L)).contains("NO_CONTACT"));
    }

    @Test
    void aFollowUpNeedsAVisibleStudentAndValidChoices() {
        when(users.findById(3L)).thenReturn(Optional.of(scholar(3, "ON_HOLD")));
        Map<String, Object> ok = new HashMap<>(Map.of("kind", "scholar", "studentId", 3, "method", "call", "outcome", "monitor"));
        assertEquals("Follow-up recorded.", service.record(ok).get("message"));
        assertThrows(ApiException.class, () -> service.record(new HashMap<>(Map.of("kind", "SCHOLAR", "studentId", 3, "method", "SMS", "outcome", "MONITOR"))));
        when(scope.current()).thenReturn(StudentScope.Scope.NONE);
        assertThrows(ApiException.class, () -> service.record(ok));
    }
}
