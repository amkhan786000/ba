package com.rahbar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Mirrors the original Flask behaviour: log and don't fail the request.
            // Spring only says "Authentication failed"; the SMTP server's own reply (the root cause) says why.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            System.err.println("Failed to send email to " + to + ": " + e.getMessage()
                    + (root != e ? " | server said: " + root.getMessage() : "")
                    + " | signed in as '" + mailUsername + "'");
        }
    }
}
