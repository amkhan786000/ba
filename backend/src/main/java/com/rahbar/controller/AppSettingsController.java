package com.rahbar.controller;

import com.rahbar.service.DisplaySettings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administration > Application Settings. */
@RestController
@RequestMapping("/api/admin/app-settings")
@PreAuthorize("denyAll()")
public class AppSettingsController {

    private final DisplaySettings display;

    public AppSettingsController(DisplaySettings display) {
        this.display = display;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APP_SETTINGS:VIEW')")
    public Map<String, Object> view() {
        return display.view();
    }

    /** Body: { dateFormat: DD/MM/YYYY | MM/DD/YYYY | YYYY-MM-DD | D MMM YYYY }. */
    @PutMapping
    @PreAuthorize("hasAuthority('APP_SETTINGS:EDIT')")
    public Map<String, String> save(@RequestBody Map<String, Object> body) {
        display.save(body);
        return Map.of("message", "Saved. Everyone sees the new date format the next time a page loads.");
    }
}
