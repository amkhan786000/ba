package com.rahbar.service;

import com.rahbar.entity.ApplicationPeriod;
import com.rahbar.entity.Role;
import com.rahbar.entity.SponsorshipApplication;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ApplicationPeriodRepository;
import com.rahbar.repository.GranteeDetailsRepository;
import com.rahbar.repository.SponsorshipApplicationRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Apply for Sponsorship: only while a session is open; sponsors for themselves, the office on their behalf. */
class SponsorApplicationServiceTest {

    private final SponsorshipApplicationRepository applications = mock(SponsorshipApplicationRepository.class);
    private final ApplicationPeriodRepository periods = mock(ApplicationPeriodRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final SponsorApplicationService service = new SponsorApplicationService(applications, periods, users,
            new SponsorPreferenceService(mock(GranteeDetailsRepository.class)), scope,
            mock(com.rahbar.repository.GrantorGranteeRepository.class), mock(GranteeDetailsRepository.class));

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private User signIn(long id, int role, String... permissions) {
        User u = new User();
        u.setId(id);
        u.setRoleId(role);
        u.setName("User " + id);
        var principal = new RahbarUserPrincipal(u, Set.of(permissions), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private void openSession() {
        ApplicationPeriod p = new ApplicationPeriod();
        p.setId(10L);
        p.setIsActive(true);
        p.setStartDate(LocalDateTime.now().minusDays(1));
        p.setEndDate(LocalDateTime.now().plusDays(30));
        when(periods.findFirstByIsActiveTrueOrderByStartDateDesc()).thenReturn(Optional.of(p));
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void noSessionMeansStayTuned() {
        signIn(5, 5, "SPONSOR_APPLY:VIEW");
        when(periods.findFirstByIsActiveTrueOrderByStartDateDesc()).thenReturn(Optional.empty());
        assertEquals(false, service.form().get("open"));
        ApiException e = assertThrows(ApiException.class, () -> service.apply(body("studentsWanted", 2)));
        assertTrue(e.getMessage().contains("Stay tuned"));
    }

    @Test
    void aSponsorAppliesForThemselvesAndItBecomesTheirPreferences() {
        User me = signIn(5, 5, "SPONSOR_APPLY:VIEW");
        openSession();
        when(applications.findByPeriodIdAndSponsorId(10L, 5L)).thenReturn(Optional.empty());

        Map<String, Object> row = service.apply(body("studentsWanted", "3", "collegeType", "govt",
                "fatherProfessions", List.of("Farmer", " farmer ", "Driver"), "incomeMin", "50000", "incomeMax", "200000"));

        verify(applications).save(any(SponsorshipApplication.class));
        assertEquals(3, row.get("students_wanted"));
        assertEquals(List.of("Farmer", "Driver"), row.get("father_professions"));
        assertEquals("GOVT", me.getPrefCollegeType());
        assertEquals("Farmer\nDriver", me.getPrefFatherProfessions());
        assertEquals(new BigDecimal("50000"), me.getPrefIncomeMin());
        verify(users).save(me);
    }

    @Test
    void checksTheNumbers() {
        signIn(5, 5, "SPONSOR_APPLY:VIEW");
        openSession();
        assertThrows(ApiException.class, () -> service.apply(body("studentsWanted", 0)));
        assertThrows(ApiException.class, () -> service.apply(body("studentsWanted", 2, "incomeMin", 9, "incomeMax", 1)));
        assertThrows(ApiException.class, () -> service.apply(body("studentsWanted", 2, "collegeType", "OTHER")));
    }

    @Test
    void onBehalfOfAnotherSponsorNeedsEdit() {
        signIn(5, 5, "SPONSOR_APPLY:VIEW");
        openSession();
        assertThrows(ApiException.class, () -> service.apply(body("sponsorId", 99, "studentsWanted", 1)));
        assertThrows(ApiException.class, () -> service.list(null));
    }

    @Test
    void someoneWhoIsNotASponsorMustChooseOne() {
        signIn(2, 2, "SPONSOR_APPLY:VIEW", "SPONSOR_APPLY:EDIT");
        openSession();
        ApiException e = assertThrows(ApiException.class, () -> service.apply(body("studentsWanted", 1)));
        assertTrue(e.getMessage().contains("Choose the sponsor"));
    }
}
