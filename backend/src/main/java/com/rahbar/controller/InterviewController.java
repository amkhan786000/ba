package com.rahbar.controller;

import com.rahbar.service.InterviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Interview criteria (managed by admins) and the ranking of interviewed applications. */
@RestController
@RequestMapping("/api/admin/interviews")
@PreAuthorize("denyAll()")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/criteria")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW') or hasAnyRole('3','4')")
    public List<Map<String, Object>> criteria() {
        return interviewService.criteria();
    }

    /** Body: { criterionId?: (absent for a new one), name, description, sortOrder, active }. */
    @PostMapping("/criteria")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT')")
    public Map<String, Object> saveCriterion(@RequestBody Map<String, Object> body) {
        Object order = body.get("sortOrder");
        return interviewService.saveCriterion(com.rahbar.util.Ids.toLong(body.get("criterionId")), (String) body.get("name"),
                (String) body.get("description"), order == null || String.valueOf(order).isBlank() ? null : Integer.valueOf(String.valueOf(order)),
                body.get("active") == null ? null : Boolean.valueOf(String.valueOf(body.get("active"))));
    }

    @DeleteMapping("/criteria/{id}")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT')")
    public Map<String, String> deleteCriterion(@PathVariable Long id) {
        interviewService.deleteCriterion(id);
        return Map.of("message", "Criterion deleted.");
    }

    /** Interviewed applications, best average score first. */
    @GetMapping("/ranking")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW') or hasAnyRole('3','4')")
    public Map<String, Object> ranking() {
        return interviewService.ranking();
    }
}
