package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.CoordinatorService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Mirrors routes/coordinator.py (role_id 3). */
@RestController
@RequestMapping("/api/coordinator")
@PreAuthorize("hasRole('3')")
public class CoordinatorController {

    private final CoordinatorService coordinatorService;

    public CoordinatorController(CoordinatorService coordinatorService) {
        this.coordinatorService = coordinatorService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) Integer year) {
        return coordinatorService.dashboard(AuthUtil.currentUser().getUserId(), year);
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> viewApplications() {
        return coordinatorService.applications();
    }

    @PostMapping("/assign-sponsor")
    public Map<String, String> assignSponsor(@RequestBody Map<String, String> body) {
        coordinatorService.assignSponsor(body.get("granteeId"), body.get("grantorId"));
        return Map.of("message", "Sponsor assigned successfully!");
    }

    @PostMapping("/users/{userId}/status/{status}")
    public Map<String, String> updateUserStatus(@PathVariable String userId, @PathVariable String status) {
        coordinatorService.updateUserStatus(userId, status);
        return Map.of("message", "User status updated to " + status + ", and grantees reassigned to default grantor (ID: 12).");
    }

    @GetMapping("/map-students/{sponsorId}")
    public Map<String, Object> mapStudentsScreen(@PathVariable String sponsorId) {
        return coordinatorService.mapStudentsScreen(sponsorId);
    }

    @PostMapping("/map-students/{sponsorId}")
    @SuppressWarnings("unchecked")
    public Map<String, String> mapStudents(@PathVariable String sponsorId, @RequestBody Map<String, Object> body) {
        coordinatorService.mapStudents(sponsorId, (List<String>) body.get("studentIds"));
        return Map.of("message", "Students mapped successfully!");
    }

    @GetMapping("/manage-sponsors")
    public Map<String, Object> manageSponsors() {
        return coordinatorService.manageSponsors();
    }

    @PostMapping("/appoint-convenor/{sponsorId}")
    public Map<String, String> appointConvenor(@PathVariable String sponsorId, @RequestBody Map<String, String> body) {
        coordinatorService.appointConvenor(sponsorId, body.get("region"));
        return Map.of("message", "Sponsor appointed as Convenor successfully!");
    }

    @PostMapping("/users/{userId}/region")
    public Map<String, String> changeRegion(@PathVariable String userId, @RequestBody Map<String, String> body) {
        coordinatorService.changeRegion(userId, body.get("region"));
        return Map.of("message", "Region updated successfully!");
    }

    @PostMapping("/assign-students-bulk")
    @SuppressWarnings("unchecked")
    public Map<String, String> assignStudentsBulk(@RequestBody Map<String, Object> body) {
        coordinatorService.assignStudentsBulk(String.valueOf(body.get("sponsorId")), (List<String>) body.get("studentIds"));
        return Map.of("message", "Students assigned to sponsor successfully!");
    }

    @GetMapping("/sponsors-convenors")
    public List<Map<String, Object>> viewSponsorsConvenors() {
        return coordinatorService.sponsorsConvenors();
    }

    @GetMapping("/monitor-payments")
    public Map<String, Object> monitorPayments(@RequestParam(defaultValue = "0") int start,
                                               @RequestParam(defaultValue = "10") int length,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "grantee_name") String orderBy,
                                               @RequestParam(defaultValue = "asc") String orderDir) {
        return coordinatorService.monitorPayments(start, length, search, orderBy, orderDir);
    }

    @GetMapping("/reports")
    public ResponseEntity<ByteArrayResource> generateReports(@RequestParam String reportType,
                                                             @RequestParam(defaultValue = "csv") String format) {
        return Downloads.of(coordinatorService.report(reportType, format));
    }
}
