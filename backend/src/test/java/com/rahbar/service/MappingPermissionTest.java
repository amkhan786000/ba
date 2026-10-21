package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Changing a student's sponsor needs Sponsorships: manage, also from Student Directory (not only Students: manage). */
class MappingPermissionTest {

    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final SponsorMappingService mapping = mock(SponsorMappingService.class);
    private final AdminService service = new AdminService(users, mock(ChapterRepository.class), mock(RoleRepository.class),
            mock(ApplicationPeriodRepository.class), mock(PaymentScheduleRepository.class), mock(RccCenterRepository.class),
            mock(CourseRepository.class), mock(InstitutionRepository.class), mock(GranteeDetailsRepository.class),
            mock(ApplicationStatusRepository.class), mappings, mock(StudentInstitutionCourseRepository.class),
            mock(BankDetailsRepository.class), mock(PaymentRepository.class), mock(StudentProgressRepository.class), mapping,
            mock(StudentService.class), mock(ApplicationService.class), mock(NotificationService.class), mock(PasswordEncoder.class),
            mock(FileStorageService.class), mock(EmailService.class), mock(com.rahbar.security.SponsorPrivacy.class),
            mock(StudentStatusHistoryRepository.class), mock(PaymentInstallmentService.class), mock(StudentScope.class),
            mock(TrustedDeviceService.class));

    private void signIn(String... permissions) {
        User me = new User();
        me.setId(1L);
        me.setRoleId(9);
        var p = new RahbarUserPrincipal(me, Set.of(permissions), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        User sponsor = new User();
        sponsor.setId(5L);
        sponsor.setRoleId(5);
        when(users.findById(5L)).thenReturn(Optional.of(sponsor));
        User sponsor2 = new User();
        sponsor2.setId(6L);
        sponsor2.setRoleId(5);
        when(users.findById(6L)).thenReturn(Optional.of(sponsor2));
        GrantorGrantee gg = new GrantorGrantee();
        gg.setGranteeId(10L);
        gg.setGrantorId(5L);
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.of(gg)); // student 10 has sponsor 5
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    @Test
    void studentsManageAloneCantChangeTheSponsor() {
        signIn("STUDENTS:EDIT", "STUDENTS:VIEW", "SPONSORSHIPS:VIEW");
        assertThrows(ApiException.class, () -> service.updateStudent(10L, new HashMap<>(Map.of("sponsorId", 6))));
        assertThrows(ApiException.class, () -> service.studentAction(10L, "unmap"));
        verifyNoInteractions(mapping);
        // saving BA Details with the same sponsor is fine
        service.updateStudent(10L, new HashMap<>(Map.of("sponsorId", 5)));
        verifyNoInteractions(mapping);
    }

    @Test
    void withSponsorshipsManageTheSponsorChanges() {
        signIn("STUDENTS:EDIT", "SPONSORSHIPS:EDIT");
        service.updateStudent(10L, new HashMap<>(Map.of("sponsorId", 6)));
        verify(mapping).map(eq(10L), eq(6L), anyString(), eq(false), isNull(), isNull(), eq(false));
    }
}
