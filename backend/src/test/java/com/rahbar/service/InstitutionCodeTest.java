package com.rahbar.service;

import com.rahbar.entity.Institution;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** An institution's Code is set when it is added (default: its name) and stays when the name is edited later. */
class InstitutionCodeTest {

    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final AdminService service = new AdminService(mock(UserRepository.class), mock(ChapterRepository.class), mock(RoleRepository.class),
            mock(ApplicationPeriodRepository.class), mock(PaymentScheduleRepository.class), mock(RccCenterRepository.class),
            mock(CourseRepository.class), institutions, mock(GranteeDetailsRepository.class),
            mock(ApplicationStatusRepository.class), mock(GrantorGranteeRepository.class), mock(StudentInstitutionCourseRepository.class),
            mock(BankDetailsRepository.class), mock(PaymentRepository.class), mock(StudentProgressRepository.class), mock(SponsorMappingService.class),
            mock(StudentService.class), mock(ApplicationService.class), mock(NotificationService.class), mock(PasswordEncoder.class),
            mock(FileStorageService.class), mock(EmailService.class), mock(com.rahbar.security.SponsorPrivacy.class),
            mock(StudentStatusHistoryRepository.class), mock(PaymentInstallmentService.class), mock(StudentScope.class),
            mock(TrustedDeviceService.class));

    private Institution added(Map<String, Object> body) {
        service.addInstitution(body);
        ArgumentCaptor<Institution> saved = ArgumentCaptor.forClass(Institution.class);
        verify(institutions, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void theCodeDefaultsToTheName() {
        assertEquals("DRIT", added(new HashMap<>(Map.of("institutionName", "DRIT"))).getInstitutionCode());
    }

    @Test
    void aCodeCanBeGivenWhenAdding() {
        assertEquals("MMANTC", added(new HashMap<>(Map.of("institutionName", "MMANTC Institute", "institutionCode", " MMANTC "))).getInstitutionCode());
    }

    @Test
    void editingTheNameKeepsTheCode() {
        Institution i = new Institution();
        i.setInstitutionId("DRIT");
        i.setInstitutionName("DRIT");
        i.setInstitutionCode("DRIT");
        when(institutions.findById("DRIT")).thenReturn(Optional.of(i));
        service.updateInstitution("DRIT", new HashMap<>(Map.of("institutionName", "Dr. Rajendra Institute of Technology", "institutionCode", "XYZ")));
        assertEquals("Dr. Rajendra Institute of Technology", i.getInstitutionName());
        assertEquals("DRIT", i.getInstitutionCode());
    }
}
