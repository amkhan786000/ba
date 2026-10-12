package com.rahbar.controller;

import com.rahbar.service.PersonMergeService;
import com.rahbar.util.Ids;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Merging a person's second account into the one to keep, so they become one account with both roles (Super Admin only). */
@RestController
@RequestMapping("/api/admin/people/merge")
@PreAuthorize("hasRole('1')")
public class PersonMergeController {

    private final PersonMergeService mergeService;

    public PersonMergeController(PersonMergeService mergeService) {
        this.mergeService = mergeService;
    }

    /** Body: { keepId, removeId } (users.id). What would move, what would stay, and the roles afterwards. */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> body) {
        return mergeService.preview(Ids.toLong(body.get("keepId")), Ids.toLong(body.get("removeId")));
    }

    @PostMapping
    public Map<String, Object> merge(@RequestBody Map<String, Object> body) {
        return mergeService.merge(Ids.toLong(body.get("keepId")), Ids.toLong(body.get("removeId")));
    }
}
