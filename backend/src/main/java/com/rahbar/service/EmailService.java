package com.rahbar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.rahbar.dto.RenderedEmail;
import com.rahbar.dto.EmailAttachment;
import jakarta.annotation.PreDestroy;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    private final EmailLogService emailLogService;
    private final EmailTemplateService templateService;

    public EmailService(JavaMailSender mailSender, EmailLogService emailLogService, EmailTemplateService templateService) {
        this.mailSender = mailSender;
        this.emailLogService = emailLogService;
        this.templateService = templateService;
    }

    /** Sends an email of this kind, worded by its template (Admin > Email Templates) with the values filled in. */
    public boolean send(EmailType type, String to, java.util.Map<String, ?> values) {
        return send(type, to, values, java.util.List.of());
    }

    public boolean send(EmailType type, String to, java.util.Map<String, ?> values, java.util.List<EmailAttachment> attachments) {
        RenderedEmail mail = templateService.render(type, values);
        return send(to, mail.subject(), mail.body(), attachments, null);
    }

    /**
     * Sends notification emails, so a request that notifies many people (e.g. mapping 30 students to a sponsor)
     * doesn't wait for the mail server once per email. A few at a time, so the mail server isn't flooded.
     */
    private final ExecutorService background = Executors.newFixedThreadPool(3, r -> new Thread(r, "email-sender"));

    /**
     * Like {@link #send(EmailType, String, java.util.Map)}, but sent in the background once the current transaction
     * has committed (nothing is sent when it rolls back). The wording is filled in now; the email log records the
     * outcome as usual.
     */
    public void sendInBackground(EmailType type, String to, java.util.Map<String, ?> values) {
        RenderedEmail mail = templateService.render(type, values);
        Runnable job = () -> send(to, mail.subject(), mail.body(), java.util.List.of(), null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { background.execute(job); }
            });
        } else {
            background.execute(job);
        }
    }

    /** On shutdown (e.g. a deploy) the emails still waiting are sent first, for up to a minute. */
    @PreDestroy
    void stop() throws InterruptedException {
        background.shutdown();
        background.awaitTermination(1, TimeUnit.MINUTES);
    }

    /** Like {@link #send(EmailType, String, java.util.Map)} for an email carrying a secret; the email log hides it. */
    public boolean sendWithSecret(EmailType type, String to, java.util.Map<String, ?> values, String secret) {
        RenderedEmail mail = templateService.render(type, values);
        return send(to, mail.subject(), mail.body(), java.util.List.of(), secret);
    }

    /** Sends a plain-text email; returns false (and logs why) when it could not be sent. */
    public boolean send(String to, String subject, String body) {
        return send(to, subject, body, java.util.List.of());
    }

    /** Sends a plain-text email with attachments; returns false (and logs why) when it could not be sent. */
    public boolean send(String to, String subject, String body, java.util.List<EmailAttachment> attachments) {
        return send(to, subject, body, attachments, null);
    }

    /**
     * Sends an email that contains a secret (a one-time code or a temporary password). The email log keeps the
     * text with the secret replaced by [hidden].
     */
    public boolean sendWithSecret(String to, String subject, String body, String secret) {
        return send(to, subject, body, java.util.List.of(), secret);
    }

    private boolean send(String to, String subject, String body, java.util.List<EmailAttachment> attachments, String secret) {
        String logged = secret == null || secret.isEmpty() || body == null ? body : body.replace(secret, "[hidden]");
        java.util.List<String> names = attachments == null ? java.util.List.of() : attachments.stream().map(EmailAttachment::name).toList();
        try {
            if (attachments == null || attachments.isEmpty()) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } else {
                jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body);
                for (EmailAttachment a : attachments) helper.addAttachment(a.name(), a.path().toFile());
                mailSender.send(message);
            }
            emailLogService.record(to, subject, logged, names, true, null);
            return true;
        } catch (Exception e) {
            // Mirrors the original Flask behaviour: log and don't fail the request.
            // Spring only says "Authentication failed"; the SMTP server's own reply (the root cause) says why.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            System.err.println("Failed to send email to " + to + ": " + e.getMessage()
                    + (root != e ? " | server said: " + root.getMessage() : "")
                    + " | signed in as '" + mailUsername + "'");
            emailLogService.record(to, subject, logged, names, false, root.getMessage() != null ? root.getMessage() : e.getMessage());
            return false;
        }
    }
}
