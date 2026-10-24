package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.CareerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Students > Careers (office) and the student portal's Careers page. */
@RestController
public class CareerController {

    private final CareerService service;

    public CareerController(CareerService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- openings

    @GetMapping("/api/admin/careers/opportunities")
    @PreAuthorize("hasAuthority('CAREERS:VIEW')")
    public List<Map<String, Object>> opportunities() {
        return service.opportunities();
    }

    @GetMapping("/api/admin/careers/opportunities/{id}/interested")
    @PreAuthorize("hasAuthority('CAREERS:VIEW')")
    public List<Map<String, Object>> interested(@PathVariable Long id) {
        return service.interested(id);
    }

    @PostMapping("/api/admin/careers/opportunities")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> addOpportunity(@RequestBody Map<String, Object> body) {
        return service.saveOpportunity(null, body);
    }

    @PutMapping("/api/admin/careers/opportunities/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> updateOpportunity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.saveOpportunity(id, body);
    }

    @DeleteMapping("/api/admin/careers/opportunities/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, String> deleteOpportunity(@PathVariable Long id) {
        service.deleteOpportunity(id);
        return Map.of("message", "Opening deleted.");
    }

    /** Body: { status: INTERESTED | APPLIED | SELECTED | NOT_SELECTED, note }. */
    @PutMapping("/api/admin/careers/interests/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> updateInterest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.updateInterest(id, body);
    }

    // ---------------------------------------------------------------- placements

    /** confirmed: true / false / empty (all). */
    @GetMapping("/api/admin/careers/placements")
    @PreAuthorize("hasAuthority('CAREERS:VIEW')")
    public List<Map<String, Object>> placements(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean confirmed) {
        return service.placements(q, confirmed);
    }

    @PostMapping("/api/admin/careers/placements")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> addPlacement(@RequestBody Map<String, Object> body) {
        return service.savePlacement(null, body);
    }

    @PutMapping("/api/admin/careers/placements/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> updatePlacement(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.savePlacement(id, body);
    }

    @PostMapping("/api/admin/careers/placements/{id}/confirm")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> confirmPlacement(@PathVariable Long id) {
        return service.confirmPlacement(id);
    }

    @DeleteMapping("/api/admin/careers/placements/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, String> deletePlacement(@PathVariable Long id) {
        service.deletePlacement(id);
        return Map.of("message", "Placement deleted.");
    }

    // ---------------------------------------------------------------- employers

    @GetMapping("/api/admin/careers/employers")
    @PreAuthorize("hasAuthority('CAREERS:VIEW')")
    public List<Map<String, Object>> employers() {
        return service.employers();
    }

    @PostMapping("/api/admin/careers/employers")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> addEmployer(@RequestBody Map<String, Object> body) {
        return service.saveEmployer(null, body);
    }

    @PutMapping("/api/admin/careers/employers/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> updateEmployer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.saveEmployer(id, body);
    }

    @DeleteMapping("/api/admin/careers/employers/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, String> deleteEmployer(@PathVariable Long id) {
        service.deleteEmployer(id);
        return Map.of("message", "Employer deleted.");
    }

    // ---------------------------------------------------------------- guidance

    @GetMapping("/api/admin/careers/guidance")
    @PreAuthorize("hasAuthority('CAREERS:VIEW')")
    public Map<String, Object> guidance(@RequestParam(required = false) String status) {
        return service.guidanceRequests(status);
    }

    /** Body: { status, assignedTo, sessionAt (2026-07-01T10:30), outcome }. */
    @PutMapping("/api/admin/careers/guidance/{id}")
    @PreAuthorize("hasAuthority('CAREERS:EDIT')")
    public Map<String, Object> updateGuidance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.updateGuidance(id, body);
    }

    // ---------------------------------------------------------------- student portal

    @GetMapping("/api/student/careers")
    @PreAuthorize("hasRole('6')")
    public Map<String, Object> mine() {
        return service.mine(AuthUtil.currentUser().getId());
    }

    /** Body: { interested: true | false }. */
    @PutMapping("/api/student/careers/opportunities/{id}/interest")
    @PreAuthorize("hasRole('6')")
    public Map<String, Object> interest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.setInterest(AuthUtil.currentUser().getId(), id, Boolean.TRUE.equals(body.get("interested")));
    }

    @PostMapping("/api/student/careers/placements")
    @PreAuthorize("hasRole('6')")
    public Map<String, Object> addMyPlacement(@RequestBody Map<String, Object> body) {
        return service.addMyPlacement(AuthUtil.currentUser().getId(), body);
    }

    /** Body: { topic, message }. */
    @PostMapping("/api/student/careers/guidance")
    @PreAuthorize("hasRole('6')")
    public Map<String, Object> askGuidance(@RequestBody Map<String, Object> body) {
        return service.askGuidance(AuthUtil.currentUser().getId(), body);
    }
}
