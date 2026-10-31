package com.rahbar.service;

import com.rahbar.entity.Course;
import com.rahbar.entity.StudentInstitutionCourse;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Courses > Move students: all students of course A go to course B in one click; A can be deleted afterwards. */
class MoveCourseStudentsTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final StudentInstitutionCourseRepository assigned = mock(StudentInstitutionCourseRepository.class);
    private final PaymentInstallmentService installments = mock(PaymentInstallmentService.class);
    private final AdminService service = new AdminService(mock(UserRepository.class), mock(ChapterRepository.class), mock(RoleRepository.class),
            mock(ApplicationPeriodRepository.class), mock(PaymentScheduleRepository.class), mock(RccCenterRepository.class),
            courses, mock(InstitutionRepository.class), mock(GranteeDetailsRepository.class),
            mock(ApplicationStatusRepository.class), mock(GrantorGranteeRepository.class), assigned,
            mock(BankDetailsRepository.class), mock(PaymentRepository.class), mock(StudentProgressRepository.class), mock(SponsorMappingService.class),
            mock(StudentService.class), mock(ApplicationService.class), mock(NotificationService.class), mock(PasswordEncoder.class),
            mock(FileStorageService.class), mock(EmailService.class), mock(com.rahbar.security.SponsorPrivacy.class),
            mock(StudentStatusHistoryRepository.class), installments, mock(StudentScope.class), mock(TrustedDeviceService.class));

    private Course course(long id, String name, String institution) {
        Course c = new Course();
        c.setCourseId(id);
        c.setCourseName(name);
        c.setInstitutionId(institution);
        when(courses.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static StudentInstitutionCourse on(long student, String institution, long course) {
        StudentInstitutionCourse s = new StudentInstitutionCourse();
        s.setUserId(student);
        s.setInstitutionId(institution);
        s.setCourseId(course);
        return s;
    }

    @Test
    void everyStudentMovesAndTheEmptyCourseIsDeleted() {
        Course dup = course(2, "Diploma(CE)", "MMANTC");
        course(1, "Diploma (CE)", "MMANTC");
        StudentInstitutionCourse a = on(7, "MMANTC", 2), b = on(8, "MMANTC", 2);
        when(assigned.findByCourseId(2L)).thenReturn(List.of(a, b));
        when(assigned.countByCourseId(2L)).thenReturn(0L);

        Map<String, Object> out = service.moveCourseStudents(2L, 1L, true);
        assertEquals(2, out.get("moved"));
        assertEquals(true, out.get("deleted"));
        assertEquals(1L, a.getCourseId());
        assertEquals(1L, b.getCourseId());
        verify(installments).sync(7L); // plans follow the new course's semesters
        verify(installments).sync(8L);
        verify(courses).delete(dup);
    }

    @Test
    void toAnotherInstitutionsCourseTheInstitutionChangesToo() {
        course(2, "B.Pharm", "OLD");
        course(5, "B.Pharm", "PATNA");
        StudentInstitutionCourse a = on(7, "OLD", 2);
        when(assigned.findByCourseId(2L)).thenReturn(List.of(a));
        service.moveCourseStudents(2L, 5L, false);
        assertEquals("PATNA", a.getInstitutionId());
        verify(courses, never()).delete(any());
    }

    @Test
    void notToItselfOrNowhere() {
        course(2, "B.Pharm", "OLD");
        assertThrows(ApiException.class, () -> service.moveCourseStudents(2L, 2L, false));
        assertThrows(ApiException.class, () -> service.moveCourseStudents(2L, null, false));
    }
}
