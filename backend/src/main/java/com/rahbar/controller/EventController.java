package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.EventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Community > Events (staff), and Events in the student and sponsor portals. */
@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    /** upcoming: true (today on), false (past), empty (all). */
    @GetMapping("/api/admin/events")
    @PreAuthorize("hasAuthority('EVENTS:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) Boolean upcoming) {
        return service.list(upcoming);
    }

    /** Body: { title, kind, description, startsAt, endsAt, venue, onlineLink, audience, chapterId, rccCenterId, capacity, registrationOpen, status, notify }. */
    @PostMapping("/api/admin/events")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        return service.save(null, body);
    }

    @PutMapping("/api/admin/events/{id}")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.save(id, body);
    }

    @DeleteMapping("/api/admin/events/{id}")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public Map<String, String> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Event deleted.");
    }

    @GetMapping("/api/admin/events/{id}/registrations")
    @PreAuthorize("hasAuthority('EVENTS:VIEW')")
    public Map<String, Object> registrations(@PathVariable Long id) {
        return service.registrations(id);
    }

    /** Body: { userId } or { coachingStudentId } or { guestName, guestPhone }. */
    @PostMapping("/api/admin/events/{id}/registrations")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public Map<String, Object> addRegistration(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.addRegistration(id, body);
    }

    @DeleteMapping("/api/admin/events/{id}/registrations/{registrationId}")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public Map<String, String> removeRegistration(@PathVariable Long id, @PathVariable Long registrationId) {
        service.removeRegistration(id, registrationId);
        return Map.of("message", "Removed.");
    }

    /** Body: { attended: { "registrationId": true | false | null } }. */
    @PutMapping("/api/admin/events/{id}/attendance")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public Map<String, Object> attendance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.markAttendance(id, body);
    }

    /** People staff can add to an event (users and coaching students) matching q. */
    @GetMapping("/api/admin/events/people")
    @PreAuthorize("hasAuthority('EVENTS:EDIT')")
    public List<Map<String, Object>> people(@RequestParam(required = false) String q) {
        return service.people(q);
    }

    // ---------------------------------------------------------------- portals

    @GetMapping("/api/portal/events")
    @PreAuthorize("hasAnyRole('5', '6')")
    public List<Map<String, Object>> forMe() {
        return service.forMe(AuthUtil.currentUser());
    }

    /** Body: { registered: true | false }. */
    @PutMapping("/api/portal/events/{id}/registration")
    @PreAuthorize("hasAnyRole('5', '6')")
    public Map<String, Object> register(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.register(AuthUtil.currentUser(), id, Boolean.TRUE.equals(body.get("registered")));
    }
}
