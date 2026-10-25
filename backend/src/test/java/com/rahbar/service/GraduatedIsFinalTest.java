package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Graduated is final: once a student is marked Graduated, her study status can't be changed. */
class GraduatedIsFinalTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final PaymentInstallmentService installments = mock(PaymentInstallmentService.class);
    private final AdminService service = new AdminService(users, mock(ChapterRepository.class), mock(RoleRepository.class),
            mock(ApplicationPeriodRepository.class), mock(PaymentScheduleRepository.class), mock(RccCenterRepository.class),
            mock(CourseRepository.class), mock(InstitutionRepository.class), mock(GranteeDetailsRepository.class),
            mock(ApplicationStatusRepository.class), mock(GrantorGranteeRepository.class), mock(StudentInstitutionCourseRepository.class),
            mock(BankDetailsRepository.class), mock(PaymentRepository.class), mock(StudentProgressRepository.class), mock(SponsorMappingService.class),
            mock(StudentService.class), mock(ApplicationService.class), mock(NotificationService.class), mock(PasswordEncoder.class),
            mock(FileStorageService.class), mock(EmailService.class), mock(com.rahbar.security.SponsorPrivacy.class),
            mock(StudentStatusHistoryRepository.class), installments, scope, mock(TrustedDeviceService.class));

    private User student(String status) {
        User u = new User();
        u.setId(7L);
        u.setName("Ayesha");
        u.setRoleId(6);
        u.setStudyStatus(status);
        when(scope.requireStudent(7L)).thenReturn(u);
        return u;
    }

    @Test
    void aStudentCanGraduateEarly() {
        User u = student(StudyStatus.STUDYING);
        service.setStudyStatus(7L, "GRADUATED", "2026-10-01", "Finished early");
        assertEquals(StudyStatus.GRADUATED, u.getStudyStatus());
        verify(installments).sync(7L); // the rest of her installments: "Not needed"
    }

    @Test
    void graduatedCantBeChanged() {
        User u = student(StudyStatus.GRADUATED);
        assertThrows(ApiException.class, () -> service.setStudyStatus(7L, "STUDYING", null, null));
        assertThrows(ApiException.class, () -> service.setStudyStatus(7L, "GRADUATED", null, null));
        assertEquals(StudyStatus.GRADUATED, u.getStudyStatus());
        verify(users, never()).save(any());
    }
}
