package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Careers: openings close on their date, students' placements wait for the office, a confirmed job updates alumni. */
class CareerServiceTest {

    private final CareerOpportunityRepository opportunities = mock(CareerOpportunityRepository.class);
    private final CareerInterestRepository interests = mock(CareerInterestRepository.class);
    private final PlacementRepository placements = mock(PlacementRepository.class);
    private final AlumniProfileRepository alumni = mock(AlumniProfileRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CareerService service = new CareerService(opportunities, interests, placements, mock(EmployerRepository.class),
            mock(GuidanceRequestRepository.class), alumni, users, scope, notifications);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static CareerOpportunity opening(String status, LocalDate lastDate) {
        CareerOpportunity o = new CareerOpportunity();
        o.setId(1L);
        o.setStatus(status);
        o.setLastDate(lastDate);
        return o;
    }

    @Test
    void anOpeningIsOpenUntilItsLastDay() {
        assertTrue(CareerService.isOpen(opening("OPEN", null)));
        assertTrue(CareerService.isOpen(opening("OPEN", LocalDate.now())));
        assertFalse(CareerService.isOpen(opening("OPEN", LocalDate.now().minusDays(1))));
        assertFalse(CareerService.isOpen(opening("CLOSED", null)));
    }

    @Test
    void noInterestInAClosedOpening() {
        when(opportunities.findById(1L)).thenReturn(Optional.of(opening("CLOSED", null)));
        assertThrows(ApiException.class, () -> service.setInterest(5L, 1L, true));
        verify(interests, never()).save(any());
    }

    @Test
    void aStudentsPlacementWaitsForTheOffice() {
        User me = new User();
        me.setId(5L);
        me.setName("Sana");
        when(users.findById(5L)).thenReturn(Optional.of(me));
        service.addMyPlacement(5L, new HashMap<>(Map.of("kind", "JOB", "organisation", "AIIMS Patna")));
        verify(placements).save(argThat(p -> p.isAddedByStudent() && p.getVerifiedAt() == null && "AIIMS Patna".equals(p.getOrganisation())));
        verify(notifications).notifyRoles(eq(CareerService.OFFICE_ROLES), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aConfirmedJobOfAGraduateBecomesTheirAlumniStatus() {
        User grad = new User();
        grad.setId(5L);
        grad.setStudyStatus("GRADUATED");
        Placement p = new Placement();
        p.setKind("JOB");
        p.setOrganisation("AIIMS Patna");
        p.setRoleTitle("Lab technician");
        when(alumni.findById(5L)).thenReturn(Optional.empty());
        service.syncAlumni(grad, p);
        verify(alumni).save(argThat(a -> "EMPLOYED".equals(a.getCurrentStatus()) && "AIIMS Patna".equals(a.getOrganisation())));

        p.setKind("INTERNSHIP");
        reset(alumni);
        service.syncAlumni(grad, p);
        verify(alumni, never()).save(any());

        User studying = new User();
        studying.setId(6L);
        studying.setStudyStatus("STUDYING");
        p.setKind("JOB");
        service.syncAlumni(studying, p);
        verify(alumni, never()).save(any());
    }

    @Test
    void placementFormChecks() {
        Phase3Support.signIn(1, 1, null, null, "CAREERS:VIEW", "CAREERS:EDIT");
        User s = new User();
        s.setId(5L);
        when(scope.requireStudent(5L)).thenReturn(s);
        assertThrows(ApiException.class, () -> service.savePlacement(null, new HashMap<>(Map.of("userId", 5, "kind", "JOB"))));
        assertThrows(ApiException.class, () -> service.savePlacement(null, new HashMap<>(Map.of("kind", "JOB", "organisation", "X"))));
        assertThrows(ApiException.class, () -> service.savePlacement(null, new HashMap<>(Map.of("userId", 5, "kind", "PILOT", "organisation", "X"))));
    }
}
