package com.rahbar.controller;

import com.rahbar.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin > Dashboard (overview of everything) and the module dashboards. Each module dashboard needs the same
 * permission as the module's main screen.
 */
@RestController
@RequestMapping("/api/admin")
public class DashboardController {

    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD:VIEW')")
    public Map<String, Object> overview(@RequestParam(required = false) Integer year) {
        return dashboards.overview(year);
    }

    @GetMapping("/dashboards/students")
    @PreAuthorize("hasAuthority('STUDENTS:VIEW')")
    public Map<String, Object> students() {
        return dashboards.students();
    }

    /** Students per institution and per course: period = now (default), all, or a session year. */
    @GetMapping("/dashboards/students/enrolment")
    @PreAuthorize("hasAuthority('STUDENTS:VIEW')")
    public Map<String, Object> studentEnrolment(@RequestParam(required = false) String period) {
        return dashboards.studentEnrolment(period);
    }

    @GetMapping("/dashboards/sponsors")
    @PreAuthorize("hasAuthority('SPONSORSHIPS:VIEW')")
    public Map<String, Object> sponsors(@RequestParam(required = false) Integer year) {
        return dashboards.sponsors(year);
    }

    @GetMapping("/dashboards/applications")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW')")
    public Map<String, Object> applications(@RequestParam(required = false) Integer year) {
        return dashboards.applications(year);
    }
}
