package com.rahbar.controller;

import com.rahbar.service.ActivityLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Admin "Activity Log": searchable history of who changed what, and sign-ins. */
@RestController
@RequestMapping("/api/admin/activity")
@PreAuthorize("hasAnyRole('1','2')")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam(required = false) String userId,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        return activityLogService.search(userId, search, from, to, page, size);
    }
}
