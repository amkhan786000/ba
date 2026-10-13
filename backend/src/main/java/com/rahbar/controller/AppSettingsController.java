package com.rahbar.controller;

import com.rahbar.service.DisplaySettings;
import com.rahbar.service.SignInSettings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administration > Application Settings. */
@RestController
@RequestMapping("/api/admin/app-settings")
@PreAuthorize("denyAll()")
public class AppSettingsController {

    private final DisplaySettings display;
    private final SignInSettings signIn;

    public AppSettingsController(DisplaySettings display, SignInSettings signIn) {
        this.display = display;
        this.signIn = signIn;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APP_SETTINGS:VIEW')")
    public Map<String, Object> view() {
        Map<String, Object> body = new java.util.LinkedHashMap<>(display.view());
        body.put("remember_device_days", signIn.rememberDays());
        body.put("remember_device_max_days", SignInSettings.MAX_REMEMBER_DAYS);
        return body;
    }

    /** Body: { dateFormat: DD/MM/YYYY | MM/DD/YYYY | YYYY-MM-DD | D MMM YYYY, rememberDeviceDays: 0..90 } (either or both). */
    @PutMapping
    @PreAuthorize("hasAuthority('APP_SETTINGS:EDIT')")
    public Map<String, String> save(@RequestBody Map<String, Object> body) {
        display.save(body);
        signIn.save(body);
        return Map.of("message", "Saved.");
    }
}
