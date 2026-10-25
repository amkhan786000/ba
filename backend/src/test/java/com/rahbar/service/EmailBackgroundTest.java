package com.rahbar.service;

import com.rahbar.dto.RenderedEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Notification emails go out in the background, and only once the request's changes are saved. */
class EmailBackgroundTest {

    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final EmailLogService log = mock(EmailLogService.class);
    private final EmailTemplateService templates = mock(EmailTemplateService.class);
    private final EmailService service = new EmailService(mail, log, templates);

    EmailBackgroundTest() {
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@example.com");
        when(templates.render(any(), anyMap())).thenReturn(new RenderedEmail("New student", "Hello"));
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void sentInTheBackground() {
        service.sendInBackground(EmailType.SPONSOR_STUDENT_MAPPED, "asha@example.com", Map.of());
        verify(mail, timeout(2000)).send(any(SimpleMailMessage.class));
        verify(log, timeout(2000)).record(eq("asha@example.com"), eq("New student"), eq("Hello"), anyList(), eq(true), isNull());
    }

    @Test
    void onlyAfterTheChangesAreSaved() throws InterruptedException {
        TransactionSynchronizationManager.initSynchronization();
        service.sendInBackground(EmailType.SPONSOR_STUDENT_MAPPED, "asha@example.com", Map.of());
        Thread.sleep(200);
        verify(mail, never()).send(any(SimpleMailMessage.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(mail, timeout(2000)).send(any(SimpleMailMessage.class));
    }
}
