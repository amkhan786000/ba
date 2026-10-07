package com.rahbar.controller;

import com.rahbar.util.Ids;
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
        return coordinatorService.dashboard(AuthUtil.currentUser().getId(), year);
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> viewApplications() {
        return coordinatorService.applications();
    }

    @PostMapping("/assign-sponsor")
    public Map<String, String> assignSponsor(@RequestBody Map<String, Object> body) {
        coordinatorService.assignSponsor(Ids.toLong(body.get("granteeId")), Ids.toLong(body.get("grantorId")));
        return Map.of("message", "Sponsor assigned successfully!");
    }

    @PostMapping("/users/{userId}/status/{status}")
    public Map<String, String> updateUserStatus(@PathVariable Long userId, @PathVariable String status) {
        coordinatorService.updateUserStatus(userId, status);
        return Map.of("message", "User status updated to " + status + ", and grantees reassigned to the default grantor (user 12).");
    }

    @GetMapping("/map-students/{sponsorId}")
    public Map<String, Object> mapStudentsScreen(@PathVariable Long sponsorId) {
        return coordinatorService.mapStudentsScreen(sponsorId);
    }

    @PostMapping("/map-students/{sponsorId}")
    public Map<String, String> mapStudents(@PathVariable Long sponsorId, @RequestBody Map<String, Object> body) {
        coordinatorService.mapStudents(sponsorId, Ids.toLongs(body.get("studentIds")));
        return Map.of("message", "Students mapped successfully!");
    }

    @GetMapping("/manage-sponsors")
    public Map<String, Object> manageSponsors() {
        return coordinatorService.manageSponsors();
    }

    @PostMapping("/appoint-convenor/{sponsorId}")
    public Map<String, String> appointConvenor(@PathVariable Long sponsorId, @RequestBody Map<String, Object> body) {
        coordinatorService.appointConvenor(sponsorId, body.get("chapterId"));
        return Map.of("message", "Sponsor appointed as Convenor successfully!");
    }

    @PostMapping("/users/{userId}/chapter")
    public Map<String, String> changeChapter(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
        coordinatorService.changeChapter(userId, body.get("chapterId"));
        return Map.of("message", "Chapter updated successfully!");
    }

    @PostMapping("/assign-students-bulk")
    public Map<String, String> assignStudentsBulk(@RequestBody Map<String, Object> body) {
        coordinatorService.assignStudentsBulk(Ids.toLong(body.get("sponsorId")), Ids.toLongs(body.get("studentIds")));
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
