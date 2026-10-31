package com.rahbar.service;

import com.rahbar.entity.Course;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A course's Code is set when it is added (default: its name) and stays when the name is edited later. */
class CourseCodeTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final StudentInstitutionCourseRepository assigned = mock(StudentInstitutionCourseRepository.class);
    private final AdminService service = new AdminService(mock(UserRepository.class), mock(ChapterRepository.class), mock(RoleRepository.class),
            mock(ApplicationPeriodRepository.class), mock(PaymentScheduleRepository.class), mock(RccCenterRepository.class),
            courses, mock(InstitutionRepository.class), mock(GranteeDetailsRepository.class),
            mock(ApplicationStatusRepository.class), mock(GrantorGranteeRepository.class), assigned,
            mock(BankDetailsRepository.class), mock(PaymentRepository.class), mock(StudentProgressRepository.class), mock(SponsorMappingService.class),
            mock(StudentService.class), mock(ApplicationService.class), mock(NotificationService.class), mock(PasswordEncoder.class),
            mock(FileStorageService.class), mock(EmailService.class), mock(com.rahbar.security.SponsorPrivacy.class),
            mock(StudentStatusHistoryRepository.class), mock(PaymentInstallmentService.class), mock(StudentScope.class),
            mock(TrustedDeviceService.class));

    CourseCodeTest() {
        when(courses.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(assigned.findByCourseId(any())).thenReturn(List.of());
    }

    private static Course course(Long id, String name, String code) {
        Course c = new Course();
        c.setCourseId(id);
        c.setCourseName(name);
        c.setCourseCode(code);
        return c;
    }

    @Test
    void addingWithoutACodeUsesTheName() {
        assertEquals("Diploma (CE)", service.saveCourse(course(null, "Diploma (CE)", " ")).getCourseCode());
        assertEquals("DCE", service.saveCourse(course(null, "Diploma (CE)", " DCE ")).getCourseCode());
    }

    @Test
    void editingTheNameKeepsTheCode() {
        when(courses.findById(4L)).thenReturn(Optional.of(course(4L, "Dip CE", "Dip CE")));
        Course saved = service.saveCourse(course(4L, "Diploma in Civil Engineering", "something else"));
        assertEquals("Diploma in Civil Engineering", saved.getCourseName());
        assertEquals("Dip CE", saved.getCourseCode());
    }
}
