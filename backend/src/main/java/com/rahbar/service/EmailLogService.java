package com.rahbar.service;

import com.rahbar.config.AuditConfig;
import com.rahbar.entity.EmailLog;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.EmailLogRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.SponsorPrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Keeps a copy of every email the application sends (Admin > Email Log), as evidence of what was sent to whom
 * and when. Each entry is saved in its own transaction, so it is kept even when the action that sent it fails.
 */
@Service
public class EmailLogService {

    private static final Logger log = LoggerFactory.getLogger(EmailLogService.class);
    private static final int MAX_BODY = 200_000;

    private final EmailLogRepository emailLogRepository;
    private final UserRepository userRepository;
    private final SponsorPrivacy sponsorPrivacy;

    public EmailLogService(EmailLogRepository emailLogRepository, UserRepository userRepository, SponsorPrivacy sponsorPrivacy) {
        this.emailLogRepository = emailLogRepository;
        this.userRepository = userRepository;
        this.sponsorPrivacy = sponsorPrivacy;
    }

    /** Records one email; never throws (a logging problem must not stop the email). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String to, String subject, String body, List<String> attachmentNames, boolean sent, String error) {
        try {
            EmailLog e = new EmailLog();
            e.setToAddress(cut(to == null ? "" : to.trim(), 255));
            if (to != null && !to.isBlank()) {
                userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(to.trim()).ifPresent(u -> e.setRecipientUserId(u.getId()));
            }
            e.setSubject(cut(subject, 500));
            e.setBody(cut(body, MAX_BODY));
            e.setAttachments(attachmentNames == null || attachmentNames.isEmpty() ? null : cut(String.join(", ", attachmentNames), 2000));
            e.setStatus(sent ? EmailLog.SENT : EmailLog.FAILED);
            e.setError(cut(error, 1000));
            e.setSentBy(AuditConfig.currentUserId());
            e.setSentAt(LocalDateTime.now());
            emailLogRepository.save(e);
        } catch (Exception ex) {
            log.warn("Could not record the email to {} in the email log: {}", to, ex.getMessage());
        }
    }

    /** One page of the log, newest first. Sponsors' addresses are hidden from users who may not see sponsor details. */
    @Transactional(readOnly = true)
    public Map<String, Object> search(String q, String status, Long recipientId, String from, String to, int page, int size) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        Page<EmailLog> result = emailLogRepository.search(pattern, st, recipientId, day(from), day(to) == null ? null : day(to).plusDays(1),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "sentAt", "emailId")));

        Set<Long> ids = new HashSet<>();
        result.getContent().forEach(e -> { if (e.getRecipientUserId() != null) ids.add(e.getRecipientUserId()); if (e.getSentBy() != null) ids.add(e.getSentBy()); });
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));
        boolean seeSponsors = !sponsorPrivacy.hidesDetails();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmailLog e : result.getContent()) {
            User r = e.getRecipientUserId() == null ? null : users.get(e.getRecipientUserId());
            boolean hidden = !seeSponsors && r != null && ServiceSupport.SPONSOR_ROLES.contains(r.getRoleId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("email_id", e.getEmailId());
            row.put("sent_at", e.getSentAt());
            row.put("to_address", hidden ? "(sponsor's email hidden)" : e.getToAddress());
            row.put("recipient_id", r == null ? null : r.getId());
            row.put("recipient_code", r == null ? null : r.getUserId());
            row.put("recipient_name", r == null ? null : r.getName());
            row.put("subject", e.getSubject());
            row.put("attachments", e.getAttachments());
            row.put("status", e.getStatus());
            row.put("error", e.getError());
            row.put("sent_by_name", e.getSentBy() == null ? null : Optional.ofNullable(users.get(e.getSentBy())).map(User::getName).orElse(null));
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.getTotalElements());
        body.put("rows", rows);
        return body;
    }

    /** The full text of one email. */
    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        EmailLog e = emailLogRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found."));
        User r = e.getRecipientUserId() == null ? null : userRepository.findById(e.getRecipientUserId()).orElse(null);
        boolean hidden = !!sponsorPrivacy.hidesDetails() && r != null && ServiceSupport.SPONSOR_ROLES.contains(r.getRoleId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email_id", e.getEmailId());
        row.put("sent_at", e.getSentAt());
        row.put("to_address", hidden ? "(sponsor's email hidden)" : e.getToAddress());
        row.put("recipient_code", r == null ? null : r.getUserId());
        row.put("recipient_name", r == null ? null : r.getName());
        row.put("subject", e.getSubject());
        row.put("body", e.getBody());
        row.put("attachments", e.getAttachments());
        row.put("status", e.getStatus());
        row.put("error", e.getError());
        return row;
    }

    private static LocalDateTime day(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim()).atStartOfDay();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dates must look like 2026-01-31.");
        }
    }

    private static String cut(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

}
