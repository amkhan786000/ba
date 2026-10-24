package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Impact report helpers: the biggest groups and percentages. */
class ImpactReportServiceTest {

    @Test
    void keepsTheBiggestGroupsAndAddsTheRestAsOthers() {
        Map<String, Long> counts = new HashMap<>();
        for (int i = 1; i <= 10; i++) counts.put("C" + i, (long) i);
        Map<String, Long> top = ImpactReportService.top(counts);
        assertEquals(ImpactReportService.TOP + 1, top.size());
        assertEquals("C10", top.keySet().iterator().next());
        assertEquals(3L, top.get("Others")); // C1 + C2
    }

    @Test
    void percentages() {
        assertEquals(new BigDecimal("33.3"), ImpactReportService.pct(1, 3));
        assertEquals(BigDecimal.ZERO, ImpactReportService.pct(1, 0));
    }

    @Test
    void bothPdfsRender() {
        UserRepository users = mock(UserRepository.class);
        StudentScope scope = mock(StudentScope.class);
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        User sponsor = new User();
        sponsor.setId(5L);
        sponsor.setName("A Sponsor");
        when(users.findById(5L)).thenReturn(Optional.of(sponsor));
        ImpactReportService service = new ImpactReportService(users, mock(GrantorGranteeRepository.class), mock(SponsorMappingHistoryRepository.class),
                mock(StudentInstitutionCourseRepository.class), mock(CourseRepository.class), mock(InstitutionRepository.class),
                mock(StudentProgressRepository.class), mock(PaymentInstallmentRepository.class), mock(AlumniProfileRepository.class), scope,
                mock(CoachingStudentRepository.class), mock(CoachingExamRepository.class), mock(CoachingExamPrizeRepository.class),
                mock(CoachingAttendanceRepository.class), mock(CoachingAccess.class), mock(CoachingResults.class), mock(CoachingPipelineService.class));
        assertEquals("%PDF", new String(service.organisationPdf(2025), 0, 4));
        assertEquals("%PDF", new String(service.sponsorPdf(5L), 0, 4));
    }
}
