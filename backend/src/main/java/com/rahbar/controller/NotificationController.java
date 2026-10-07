package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The bell in the top bar: the signed-in user's notifications (every role). */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }

    /** Latest 30 notifications plus the unread count. */
    @GetMapping
    public Map<String, Object> mine() {
        return notificationService.mine(me());
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        return Map.of("unread", notificationService.unreadCount(me()));
    }

    @PostMapping("/{id}/read")
    public Map<String, String> markRead(@PathVariable Long id) {
        notificationService.markRead(me(), id);
        return Map.of("message", "OK");
    }

    @PostMapping("/read-all")
    public Map<String, Object> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(me()));
    }
}
