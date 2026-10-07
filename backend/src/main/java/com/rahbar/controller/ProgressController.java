package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.ProgressReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Reviewing students' progress reports (admin, office coordinator, convenor of the chapter, the student's sponsor). */
@RestController
@RequestMapping("/api/progress")
@PreAuthorize("hasAnyRole('1','2','4','5','8')")
public class ProgressController {

    private final ProgressReviewService progressReviewService;

    public ProgressController(ProgressReviewService progressReviewService) {
        this.progressReviewService = progressReviewService;
    }

    /** Body: { status: Pending | Approved | Rejected, comment } */
    @PostMapping("/{progressId}/review")
    public Map<String, Object> review(@PathVariable Long progressId, @RequestBody Map<String, String> body) {
        return progressReviewService.review(AuthUtil.currentUser(), progressId, body.get("status"), body.get("comment"));
    }
}
