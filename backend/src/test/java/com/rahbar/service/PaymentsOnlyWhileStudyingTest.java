package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** New payments only for students who are studying: on hold, graduated and dropped out are refused. */
class PaymentsOnlyWhileStudyingTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PaymentInstallmentService service = new PaymentInstallmentService(mock(PaymentInstallmentRepository.class),
            mock(GrantorGranteeRepository.class), users, mock(StudentInstitutionCourseRepository.class),
            mock(PaymentScheduleRepository.class), mock(PaymentRepository.class), mock(PlatformTransactionManager.class),
            mock(com.rahbar.security.StudentScope.class));

    private void student(String status) {
        User u = new User();
        u.setId(7L);
        u.setName("Ayesha");
        u.setStudyStatus(status);
        when(users.findById(7L)).thenReturn(Optional.of(u));
    }

    @Test
    void studyingOrNoStatusMayPay() {
        student("STUDYING");
        assertDoesNotThrow(() -> service.requireStudying(7L));
        student(null); // no status yet = studying
        assertDoesNotThrow(() -> service.requireStudying(7L));
    }

    @Test
    void onHoldGraduatedAndDroppedOutMayNot() {
        for (String s : new String[]{"ON_HOLD", "GRADUATED", "DROPPED_OUT"}) {
            student(s);
            ApiException e = assertThrows(ApiException.class, () -> service.requireStudying(7L), s);
            assertTrue(e.getMessage().contains("only be made for students who are studying"));
        }
    }
}
