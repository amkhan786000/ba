package com.rahbar.service;

import com.rahbar.entity.Course;
import com.rahbar.entity.StudentInstitutionCourse;
import com.rahbar.entity.User;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Courses page: how many students are on each course / at each institution, and who they are (only those the user may see). */
class CourseStudentCountsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final StudentInstitutionCourseRepository assigned = mock(StudentInstitutionCourseRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final AdminService service = new AdminService(users, mock(ChapterRepository.class), mock(RoleRepository.class),
            mock(ApplicationPeriodRepository.class), mock(PaymentScheduleRepository.class), mock(RccCenterRepository.class),
            courses, mock(InstitutionRepository.class), mock(GranteeDetailsRepository.class),
            mock(ApplicationStatusRepository.class), mock(GrantorGranteeRepository.class), assigned,
            mock(BankDetailsRepository.class), mock(PaymentRepository.class), mock(StudentProgressRepository.class), mock(SponsorMappingService.class),
            mock(StudentService.class), mock(ApplicationService.class), mock(NotificationService.class), mock(PasswordEncoder.class),
            mock(FileStorageService.class), mock(EmailService.class), mock(com.rahbar.security.SponsorPrivacy.class),
            mock(StudentStatusHistoryRepository.class), mock(PaymentInstallmentService.class), scope,
            mock(TrustedDeviceService.class));

    private static StudentInstitutionCourse on(long student, String institution, long course) {
        StudentInstitutionCourse s = new StudentInstitutionCourse();
        s.setUserId(student);
        s.setInstitutionId(institution);
        s.setCourseId(course);
        return s;
    }

    private static User student(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setUserId("PVT00" + id + "-2026");
        u.setStudyStatus("STUDYING");
        return u;
    }

    @Test
    void countsPerCourseAndInstitution() {
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        when(assigned.findAll()).thenReturn(List.of(on(1, "MMANTC", 10), on(2, "MMANTC", 10), on(3, "MMANTC", 11), on(4, "PATNA", 20)));
        Map<String, Object> counts = service.courseStudentCounts();
        assertEquals(Map.of(10L, 2L, 11L, 1L, 20L, 1L), counts.get("courses"));
        assertEquals(Map.of("MMANTC", 3L, "PATNA", 1L), counts.get("institutions"));

        when(scope.current()).thenReturn(StudentScope.Scope.NONE); // someone who may see no students: nothing counted
        assertEquals(Map.of(), service.courseStudentCounts().get("courses"));
    }

    @Test
    void listsTheStudentsByName() {
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        Course ce = new Course();
        ce.setCourseId(10L);
        ce.setCourseName("Diploma (CE)");
        when(courses.findAll()).thenReturn(List.of(ce));
        when(assigned.findByInstitutionId("MMANTC")).thenReturn(List.of(on(2, "MMANTC", 10), on(1, "MMANTC", 10)));
        when(users.findAllById(any())).thenReturn(List.of(student(2, "Zara"), student(1, "Ayesha")));
        List<Map<String, Object>> rows = service.studentsOnCourse(null, "MMANTC");
        assertEquals(List.of("Ayesha", "Zara"), rows.stream().map(r -> r.get("name")).toList());
        assertEquals("Diploma (CE)", rows.get(0).get("course_name"));
        assertEquals("Studying", rows.get(0).get("study_status"));
    }
}
