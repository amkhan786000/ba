package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.SponsorPrivacy;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reports > Sign-ins: by day (every day of the period, with a total), by person, by role, and who didn't sign in. */
class ReportCatalogSignInReportsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ActivityLogService log = mock(ActivityLogService.class);
    private final StudentScope scope = mock(StudentScope.class);
    private ReportCatalogService service;

    private final LocalDate from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 3);
    private final User admin = user(1L, "A1", "Asha", 2), sponsor = user(2L, "S1", "Bilal", 5), idle = user(3L, "S2", "Chand", 5);

    private static User user(Long id, String code, String name, int role) {
        User u = new User();
        u.setId(id);
        u.setUserId(code);
        u.setName(name);
        u.setRoleId(role);
        u.setStatus("Active");
        return u;
    }

    private static ActivityLog entry(Long userId, String action, int day, int hour) {
        ActivityLog a = new ActivityLog();
        a.setUserId(userId);
        a.setAction(action);
        a.setCreatedAt(LocalDateTime.of(2026, 10, day, hour, 0));
        return a;
    }

    private static Role role(int id, String name) {
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleName(name);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new ReportCatalogService(mock(SponsorPrivacy.class), users, roles, mock(GranteeDetailsRepository.class),
                mock(ApplicationStatusRepository.class), mock(GrantorGranteeRepository.class), mock(StudentInstitutionCourseRepository.class),
                mock(InstitutionRepository.class), mock(CourseRepository.class), mock(BankDetailsRepository.class), mock(PaymentRepository.class),
                mock(PaymentScheduleRepository.class), mock(StudentProgressRepository.class), mock(RccCenterRepository.class),
                mock(PaymentReminderService.class), log, mock(ReportService.class), scope,
                mock(PaymentInstallmentRepository.class), mock(SponsorMappingHistoryRepository.class), mock(VolunteerApplicationRepository.class),
                mock(ChapterRepository.class), mock(AlumniService.class), mock(MentoringService.class));
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        when(users.findAll()).thenReturn(List.of(admin, sponsor, idle));
        when(users.findAllById(any())).thenReturn(List.of(admin, sponsor));
        when(roles.findAll()).thenReturn(List.of(role(2, "Application Admin"), role(5, "Sponsor")));
        when(log.signIns(from, to)).thenReturn(List.of(entry(1L, "Signed in", 1, 9), entry(1L, "Signed in", 1, 15),
                entry(2L, "Signed in (remembered device, no code needed)", 1, 10), entry(1L, "Signed in", 3, 9)));
        when(log.failedSignIns(from, to)).thenReturn(List.of(entry(2L, "Failed sign-in: wrong password", 2, 8)));
        when(log.lastSignIns()).thenReturn(Map.of(1L, LocalDateTime.of(2026, 10, 3, 9, 0)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String key) {
        return (List<Map<String, Object>>) service.data(key, from, to).get("rows");
    }

    @Test
    void byDayHasEveryDayOfThePeriodAndATotal() {
        List<Map<String, Object>> r = rows("sign_ins_by_day");
        assertEquals(4, r.size()); // 1, 2, 3 October and the total
        assertEquals(3L, r.get(0).get("sign_ins"));
        assertEquals(2, r.get(0).get("people"));
        assertEquals(0L, r.get(1).get("sign_ins"));
        assertEquals(1L, r.get(1).get("failed_attempts"));
        assertEquals("Total", r.get(3).get("date"));
        assertEquals(4L, r.get(3).get("sign_ins"));
        assertEquals(2, r.get(3).get("people"));
    }

    @Test
    void byPersonMostSignInsFirst() {
        List<Map<String, Object>> r = rows("sign_ins_by_person");
        assertEquals("Asha", r.get(0).get("name"));
        assertEquals(3, r.get(0).get("sign_ins"));
        assertEquals(2L, r.get(0).get("days_signed_in"));
        assertEquals("Application Admin", r.get(0).get("role"));
    }

    @Test
    void byRoleShowsTheShareOfActiveAccounts() {
        Map<String, Object> sponsors = rows("sign_ins_by_role").stream().filter(x -> "Sponsor".equals(x.get("role"))).findFirst().orElseThrow();
        assertEquals(2L, sponsors.get("active_accounts"));
        assertEquals(1, sponsors.get("people_signed_in"));
        assertEquals("50%", sponsors.get("share_signed_in"));
    }

    @Test
    void whoDidntSignIn() {
        List<Map<String, Object>> r = rows("not_signed_in");
        assertEquals(1, r.size());
        assertEquals("Chand", r.get(0).get("name"));
        assertEquals("Never", r.get(0).get("last_sign_in"));
    }

    @Test
    void notForChapterLeads() {
        when(scope.current()).thenReturn(StudentScope.Scope.NONE);
        assertThrows(com.rahbar.exception.ApiException.class, () -> service.data("sign_ins_by_day", from, to));
    }
}
