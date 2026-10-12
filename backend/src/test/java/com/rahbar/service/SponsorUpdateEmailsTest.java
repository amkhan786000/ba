package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Payment;
import com.rahbar.entity.User;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sponsors hear (in the app and by email) when their student uploads a progress report or a spent proof. */
class SponsorUpdateEmailsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final StudentProgressRepository progress = mock(StudentProgressRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FileStorageService files = mock(FileStorageService.class);
    private final StudentService service = new StudentService(users, mappings, payments, mock(BankDetailsRepository.class),
            mock(StudentInstitutionCourseRepository.class), progress, files, notifications,
            mock(PaymentInstallmentService.class));

    private final MultipartFile file = mock(MultipartFile.class);

    SponsorUpdateEmailsTest() {
        User asha = new User();
        asha.setId(11L);
        asha.setName("Asha");
        when(users.findById(11L)).thenReturn(Optional.of(asha));
        when(file.getOriginalFilename()).thenReturn("marks.pdf");
        when(files.sanitizeFilename(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aProgressReportEmailsTheSponsor() {
        GrantorGrantee gg = new GrantorGrantee();
        gg.setGrantorId(5L);
        gg.setGranteeId(11L);
        when(mappings.findFirstByGranteeId(11L)).thenReturn(Optional.of(gg));
        service.submitProgress(11L, "82%", "2026", "Semester 2", file);
        verify(notifications).notify(eq(5L), anyString(), contains("Asha"), eq(NotificationService.PROGRESS), eq("/sponsor/progress"),
                eq(EmailType.SPONSOR_PROGRESS_UPLOADED), argThat(v -> "82%".equals(v.get("marks")) && "Asha".equals(v.get("student_name"))));
    }

    @Test
    void aSpentProofEmailsThePayingSponsor() {
        Payment p = new Payment();
        p.setPaymentId(9L);
        p.setGrantorId(5L);
        p.setGranteeId(11L);
        p.setAmount(new BigDecimal("6000.00"));
        when(payments.findByPaymentIdAndGranteeId(9L, 11L)).thenReturn(Optional.of(p));
        service.uploadPaymentProof(11L, 9L, file);
        verify(notifications).notify(eq(5L), anyString(), anyString(), eq(NotificationService.PAYMENT), eq("/sponsor/payments"),
                eq(EmailType.SPONSOR_SPENT_PROOF_UPLOADED), argThat(v -> "6000".equals(v.get("amount"))));
        assertNotNull(p.getStudentProofUrl());
    }
}
