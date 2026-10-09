package com.rahbar.controller;

import com.rahbar.service.StudentMergeService;
import com.rahbar.util.Ids;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Merging a duplicate student into the one to keep (Super Admin only). */
@RestController
@RequestMapping("/api/admin/students/merge")
@PreAuthorize("hasRole('1')")
public class StudentMergeController {

    private final StudentMergeService mergeService;

    public StudentMergeController(StudentMergeService mergeService) {
        this.mergeService = mergeService;
    }

    /** Body: { keepId, removeId } (users.id). What would move and what would stay. */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> body) {
        return mergeService.preview(Ids.toLong(body.get("keepId")), Ids.toLong(body.get("removeId")));
    }

    @PostMapping
    public Map<String, Object> merge(@RequestBody Map<String, Object> body) {
        return mergeService.merge(Ids.toLong(body.get("keepId")), Ids.toLong(body.get("removeId")));
    }
}
