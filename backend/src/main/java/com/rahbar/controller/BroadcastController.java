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
                                    @RequestParam(required = false) List<MultipartFile> files) throws IOException {
        Map<String, Object> parsed = objectMapper.readValue(audience, new TypeReference<Map<String, Object>>() {});
        return broadcastService.send(subject, body, parsed, files);
    }
}
