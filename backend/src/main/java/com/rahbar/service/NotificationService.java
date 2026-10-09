package com.rahbar.service;

import com.rahbar.entity.Notification;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.NotificationRepository;
import com.rahbar.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * In-app notifications (the bell in the top bar), optionally also sent by email.
 * Sending never fails the request that triggered it: problems are logged and skipped.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public static final String APPLICATION = "application";
    public static final String PAYMENT = "payment";
    public static final String REMINDER = "reminder";
    public static final String PROGRESS = "progress";
    public static final String MAPPING = "mapping";
    public static final String ACCOUNT = "account";
    public static final String ANNOUNCEMENT = "announcement";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PushService pushService;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               EmailService emailService, PushService pushService) {
        this.pushService = pushService;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /** Notifies one user in the app (and by email when {@code email} is true and the user has an address). */
    public void notify(Long userId, String title, String message, String category, String link, boolean email) {
        send(userId, title, message, category, link, null, email ? EmailType.GENERAL_NOTIFICATION : null, generic(title, message));
    }

    /**
     * Notifies one user in the app with title / message, and emails them the {@code type} email (its wording comes
     * from Admin > Email Templates; {{name}} is the user's name, the other placeholders come from {@code values}).
     */
    public void notify(Long userId, String title, String message, String category, String link,
                       EmailType type, Map<String, ?> values) {
        send(userId, title, message, category, link, null, type, values);
    }

    /**
     * Like {@link #notify} but only once per {@code refKey}: returns false (and sends nothing) when a
     * notification with that key already exists. Used by the payment reminders.
     */
    public boolean notifyOnce(String refKey, Long userId, String title, String message, String category,
                              String link, boolean email) {
        if (refKey != null && notificationRepository.existsByRefKey(refKey)) return false;
        return send(userId, title, message, category, link, refKey, email ? EmailType.GENERAL_NOTIFICATION : null, generic(title, message));
    }

    /** {@link #notifyOnce} that emails the {@code type} email. */
    public boolean notifyOnce(String refKey, Long userId, String title, String message, String category, String link,
                              EmailType type, Map<String, ?> values) {
        if (refKey != null && notificationRepository.existsByRefKey(refKey)) return false;
        return send(userId, title, message, category, link, refKey, type, values);
    }

    private static Map<String, Object> generic(String title, String message) {
        Map<String, Object> v = new HashMap<>();
        v.put("title", title);
        v.put("message", message);
        return v;
    }

    /** Notifies every active user with one of the roles. */
    public void notifyRoles(List<Integer> roleIds, String title, String message, String category, String link) {
        for (User u : userRepository.findByRoleIdIn(roleIds)) {
            if (!"Inactive".equalsIgnoreCase(u.getStatus())) notify(u.getId(), title, message, category, link, false);
        }
    }

    private boolean send(Long userId, String title, String message, String category, String link, String refKey,
                         EmailType emailType, Map<String, ?> values) {
        if (userId == null) return false;
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setTitle(title);
            n.setMessage(message);
            n.setCategory(category);
            n.setLink(link);
            n.setRefKey(refKey);
            n.setStatus("Unread");
            n.setNotificationDate(LocalDate.now());
            notificationRepository.save(n);
        } catch (Exception e) {
            log.warn("Could not save notification for {}: {}", userId, e.getMessage());
            return false;
        }
        pushService.sendToUser(userId, title, message, link); // browsers / phones the user switched on (background)
        if (emailType != null) {
            userRepository.findById(userId)
                    .filter(u -> u.getEmail() != null && u.getEmail().contains("@") && !u.getEmail().endsWith("@rahbar.com"))
                    .ifPresent(u -> {
                        Map<String, Object> v = new HashMap<>(values == null ? Map.of() : values);
                        v.put("name", u.getName());
                        emailService.send(emailType, u.getEmail(), v);
                    });
        }
        return true;
    }

    // ---------------------------------------------------------------- the user's own notifications

    public Map<String, Object> mine(Long userId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Notification n : notificationRepository.findTop30ByUserIdOrderByNotificationIdDesc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", n.getNotificationId());
            row.put("title", n.getTitle() != null ? n.getTitle() : "Notification");
            row.put("message", n.getMessage());
            row.put("category", n.getCategory());
            row.put("link", n.getLink());
            row.put("read", "Read".equalsIgnoreCase(n.getStatus()));
            row.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt()
                    : n.getNotificationDate() != null ? n.getNotificationDate().atStartOfDay() : null);
            items.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", unreadCount(userId));
        result.put("items", items);
        return result;
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, "Unread");
    }

    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (!"Read".equalsIgnoreCase(n.getStatus())) {
            n.setStatus("Read");
            notificationRepository.save(n);
        }
    }

    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }
}
