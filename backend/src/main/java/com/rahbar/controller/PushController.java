package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.PushService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Browser push notifications: switch them on / off for the current browser, and send a test. */
@RestController
@RequestMapping("/api/push")
@PreAuthorize("isAuthenticated()")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    /** The application's public key the browser subscribes with, and how many devices the user has on. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("publicKey", pushService.publicKey(), "devices", pushService.deviceCount(me()));
    }

    /** Body: the browser's PushSubscription JSON: { endpoint, keys: { p256dh, auth } }. */
    @PostMapping("/subscribe")
    @SuppressWarnings("unchecked")
    public Map<String, Object> subscribe(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> keys = body.get("keys") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        pushService.subscribe(me(), (String) body.get("endpoint"), (String) keys.get("p256dh"), (String) keys.get("auth"),
                request.getHeader("User-Agent"));
        return Map.of("message", "Notifications are on for this device.", "devices", pushService.deviceCount(me()));
    }

    /** Body: { endpoint }. */
    @PostMapping("/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestBody Map<String, Object> body) {
        pushService.unsubscribe(me(), (String) body.get("endpoint"));
        return Map.of("message", "Notifications are off for this device.", "devices", pushService.deviceCount(me()));
    }

    @PostMapping("/test")
    public Map<String, Object> test() {
        int sent = pushService.sendTest(me());
        return Map.of("sent", sent, "message", sent > 0 ? "A test notification was sent to " + sent + " device(s)."
                : "No device accepted the test notification. Switch notifications on again on this device.");
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }
}
