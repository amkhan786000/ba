package com.rahbar.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahbar.service.BroadcastService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;
import java.util.Map;

/** Admin > Broadcast Messages: history (MESSAGES:VIEW) and sending (MESSAGES:EDIT). */
@RestController
@RequestMapping("/api/admin/broadcasts")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class BroadcastController {

    private final BroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    public BroadcastController(BroadcastService broadcastService, ObjectMapper objectMapper) {
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MESSAGES:VIEW')")
    public List<Map<String, Object>> history() {
        return broadcastService.history();
    }

    @GetMapping("/recipients")
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public List<Map<String, Object>> searchUsers(@RequestParam(required = false) String q) {
        return broadcastService.searchUsers(q);
    }

    /** Body: { "type": "USERS" | "ROLES" | "CHAPTER_LEADS", "userIds": [...], "roleIds": [...] }. */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public Map<String, Object> preview(@RequestBody Map<String, Object> audience) {
        return broadcastService.preview(audience);
    }

    /**
     * Multipart form: subject, body, audience (JSON, as in preview) and optional files (up to 5, 15 MB together),
     * which are attached to every email.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public Map<String, Object> send(@RequestParam String subject, @RequestParam String body, @RequestParam String audience,
                                    @RequestParam(required = false) List<MultipartFile> files,
                                    @RequestParam(required = false) String scheduledAt) throws IOException {
        Map<String, Object> parsed = objectMapper.readValue(audience, new TypeReference<Map<String, Object>>() {});
        return broadcastService.send(subject, body, parsed, files, scheduleTime(scheduledAt));
    }

    /**
     * scheduledAt is an ISO instant from the browser (e.g. 2026-10-08T06:00:00.000Z), turned into the server's local
     * time; a plain local date-time (no zone) is taken as server time.
     */
    private static java.time.LocalDateTime scheduleTime(String scheduledAt) {
        if (scheduledAt == null || scheduledAt.isBlank()) return null;
        String v = scheduledAt.trim();
        try {
            if (v.endsWith("Z") || v.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return java.time.OffsetDateTime.parse(v).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }
            return java.time.LocalDateTime.parse(v);
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "The scheduled time is not valid.");
        }
    }

    /** Cancels a scheduled message that hasn't gone out yet. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public Map<String, String> cancel(@PathVariable Long id) {
        broadcastService.cancel(id);
        return Map.of("message", "The scheduled message was cancelled.");
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public List<Map<String, Object>> templates() {
        return broadcastService.templates();
    }

    /** Body: { "name": "...", "subject": "...", "body": "..." }; the same name replaces the old template. */
    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public Map<String, Object> saveTemplate(@RequestBody Map<String, String> body) {
        return broadcastService.saveTemplate(body.get("name"), body.get("subject"), body.get("body"));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('MESSAGES:EDIT')")
    public void deleteTemplate(@PathVariable Long id) {
        broadcastService.deleteTemplate(id);
    }
}
