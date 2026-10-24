package com.rahbar.service;

import com.rahbar.entity.CoachingStudent;
import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Class 10 -> scholarship: applications found by name + a mobile, stages and the funnel, email needs an address. */
class CoachingPipelineServiceTest {

    private final CoachingStudentRepository students = mock(CoachingStudentRepository.class);
    private final ScholarshipApplicationRepository applications = mock(ScholarshipApplicationRepository.class);
    private final CoachingAccess access = mock(CoachingAccess.class);
    private final CoachingService coaching = new CoachingService(students, mock(CoachingTeacherRepository.class), mock(CoachingSubjectRepository.class),
            mock(CoachingExamSubjectRepository.class), mock(CoachingExamMarkRepository.class), mock(UserRepository.class), mock(RoleRepository.class), access);
    private final CoachingPipelineService service = new CoachingPipelineService(students, applications, mock(UserRepository.class), access, coaching,
            mock(CoachingResults.class), mock(CoachingAttendanceService.class), mock(PublicService.class), mock(EmailService.class), "http://x/apply");

    private static CoachingStudent student(long id, String name, String mobile) {
        CoachingStudent s = new CoachingStudent();
        s.setId(id);
        s.setName(name);
        s.setGuardianMobile(mobile);
        s.setRccCenterId(10L);
        s.setClassLevel(10);
        return s;
    }

    private static ScholarshipApplication app(long id, String name, String fatherMobile, String status) {
        ScholarshipApplication a = new ScholarshipApplication();
        a.setId(id);
        a.setName(name);
        a.setFatherMobile(fatherMobile);
        a.setStatus(status);
        return a;
    }

    @Test
    void findsTheApplicationByNameAndAMobile() {
        CoachingStudent sana = student(1, "Sana  Parveen", "9876543210");
        CoachingStudent other = student(2, "Imran", "9000000000");
        when(students.findAll()).thenReturn(List.of(sana, other));
        when(applications.findAll()).thenReturn(List.of(app(50, "sana parveen", "+91 98765 43210", ScholarshipApplication.ACCEPTED),
                app(51, "Imran", "9111111111", "Applied")));
        Map<Long, ScholarshipApplication> linked = service.link(List.of(sana, other));
        assertEquals(50L, linked.get(1L).getId());
        assertNull(linked.get(2L)); // same name, different mobile: not them
        assertEquals(50L, sana.getApplicationId());
        assertEquals("ACCEPTED", CoachingPipelineService.stage(sana, linked.get(1L)));
        assertEquals("READY", CoachingPipelineService.stage(other, null));
    }

    @Test
    void theFunnelCountsEveryoneWhoGotThatFar() {
        List<Map<String, Object>> rows = List.of(Map.of("stage", "READY"), Map.of("stage", "INVITED"), Map.of("stage", "APPLIED"), Map.of("stage", "ACCEPTED"));
        List<Map<String, Object>> f = CoachingPipelineService.funnel(rows);
        assertEquals(List.of(4L, 3L, 2L, 1L), f.stream().map(m -> m.get("value")).toList());
    }

    @Test
    void anEmailInviteNeedsAnEmail() {
        CoachingStudent s = student(1, "Sana", "9876543210");
        when(students.findById(1L)).thenReturn(Optional.of(s));
        when(access.centerIds()).thenReturn(Set.of(10L));
        doCallRealMethod().when(access).requireVisible(any(), any());
        assertThrows(ApiException.class, () -> service.emailInvite(1L));
    }
}
