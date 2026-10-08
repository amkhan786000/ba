package com.rahbar.controller;

import com.rahbar.service.StudentReminderService;
import com.rahbar.util.Ids;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Progress Due Dates: the dates students must upload a progress report by, and their reminders. */
@RestController
@RequestMapping("/api/admin/progress-due-dates")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class ProgressDueDateController {

    private final StudentReminderService reminderService;

    public ProgressDueDateController(StudentReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROGRESS_DUE_DATES:VIEW')")
    public List<Map<String, Object>> list() {
        return reminderService.dueDates();
    }

    /** Body: { "dueId": (absent for a new one), "title": "...", "dueDate": "yyyy-MM-dd", "note": "..." }. */
    @PostMapping
    @PreAuthorize("hasAuthority('PROGRESS_DUE_DATES:EDIT')")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        return reminderService.saveDueDate(Ids.toLong(body.get("dueId")), (String) body.get("title"),
                (String) body.get("dueDate"), (String) body.get("note"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROGRESS_DUE_DATES:EDIT')")
    public void delete(@PathVariable Long id) {
        reminderService.deleteDueDate(id);
    }

    /** Sends today's student reminders now (they also go out every morning on their own). */
    @PostMapping("/reminders/run")
    @PreAuthorize("hasAuthority('PROGRESS_DUE_DATES:EDIT')")
    public Map<String, Integer> runReminders() {
        return reminderService.sendReminders();
    }
}
