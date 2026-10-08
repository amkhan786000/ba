package com.rahbar.controller;

import com.rahbar.service.ChapterDashboardService;
import com.rahbar.service.DataQualityService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin > Chapter Dashboard and Admin > Data Quality. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class InsightsController {

    private final ChapterDashboardService chapterDashboardService;
    private final DataQualityService dataQualityService;

    public InsightsController(ChapterDashboardService chapterDashboardService, DataQualityService dataQualityService) {
        this.chapterDashboardService = chapterDashboardService;
        this.dataQualityService = dataQualityService;
    }

    /** chapterId is ignored for users who only cover their own chapter. */
    @GetMapping("/chapter-dashboard")
    @PreAuthorize("hasAuthority('CHAPTER_DASHBOARD:VIEW')")
    public Map<String, Object> chapterDashboard(@RequestParam(required = false) Long chapterId) {
        return chapterDashboardService.dashboard(chapterId);
    }

    @GetMapping("/data-quality")
    @PreAuthorize("hasAuthority('DATA_QUALITY:VIEW')")
    public List<Map<String, Object>> dataQuality() {
        return dataQualityService.checks();
    }
}
