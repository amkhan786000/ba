package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.entity.VolunteerHours;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import com.rahbar.repository.VolunteerApplicationRepository;
import com.rahbar.repository.VolunteerHoursRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Volunteer hours: up to 24 a day, self-logged ones wait for approval, certificates count approved hours only. */
class VolunteerHoursServiceTest {

    private final VolunteerHoursRepository hours = mock(VolunteerHoursRepository.class);
    private final VolunteerApplicationRepository volunteers = mock(VolunteerApplicationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final VolunteerHoursService service = new VolunteerHoursService(hours, volunteers, users, notifications);

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private static User me() {
        User u = new User();
        u.setId(50L);
        u.setName("Rehan");
        return u;
    }

    @Test
    void selfLoggedHoursWaitForTheOffice() {
        service.logMine(me(), new HashMap<>(Map.of("activity", "TUTORING", "hours", "2.5")));
        verify(hours).save(argThat(h -> "PENDING".equals(h.getStatus()) && h.isSelfLogged() && h.getUserId() == 50L
                && h.getHours().compareTo(new BigDecimal("2.5")) == 0));
        verify(notifications).notifyRoles(eq(CareerService.OFFICE_ROLES), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void hoursMustBeBetweenZeroAndADay() {
        assertThrows(ApiException.class, () -> service.logMine(me(), new HashMap<>(Map.of("hours", "0"))));
        assertThrows(ApiException.class, () -> service.logMine(me(), new HashMap<>(Map.of("hours", "25"))));
        assertThrows(ApiException.class, () -> service.logMine(me(), new HashMap<>(Map.of("hours", "2", "workedOn", LocalDate.now().plusDays(1).toString()))));
    }

    @Test
    void staffLogApprovedHoursForAVolunteer() {
        Phase3Support.signIn(1, 2, null, null, "VOLUNTEER_HOURS:EDIT");
        VolunteerApplication v = new VolunteerApplication();
        v.setVolunteerId(9L);
        when(volunteers.findById(9L)).thenReturn(Optional.of(v));
        service.log(new HashMap<>(Map.of("personKind", "VOLUNTEER", "personId", 9, "activity", "EVENT", "hours", 3)));
        verify(hours).save(argThat(h -> "APPROVED".equals(h.getStatus()) && h.getVolunteerId() == 9L));
    }

    private static VolunteerHours row(String status, String h) {
        VolunteerHours x = new VolunteerHours();
        x.setUserId(50L);
        x.setActivity("MENTORING");
        x.setWorkedOn(LocalDate.of(2026, 9, 1));
        x.setHours(new BigDecimal(h));
        x.setStatus(status);
        return x;
    }

    @Test
    void theCertificateCountsApprovedHoursOnly() {
        when(users.findById(50L)).thenReturn(Optional.of(me()));
        when(hours.findByUserIdOrderByWorkedOnDesc(50L)).thenReturn(List.of(row("PENDING", "4")));
        assertThrows(ApiException.class, () -> service.certificate("USER", 50L, null, null));
        when(hours.findByUserIdOrderByWorkedOnDesc(50L)).thenReturn(List.of(row("APPROVED", "2"), row("PENDING", "4")));
        assertEquals("%PDF", new String(service.certificate("USER", 50L, null, null), 0, 4));
    }
}
