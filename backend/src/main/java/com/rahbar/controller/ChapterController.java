package com.rahbar.controller;

import com.rahbar.entity.Chapter;
import com.rahbar.service.ChapterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chapters: managed by the Super Admin / Application Administrator. Every signed-in user may read the list,
 * since users pick their chapter (Add User, student forms, profile, convenor's own chapter).
 */
@RestController
@RequestMapping("/api/admin/chapters")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Chapter> list() {
        return chapterService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CHAPTERS:EDIT')")
    public Chapter save(@RequestBody Chapter chapter) {
        return chapterService.save(chapter);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CHAPTERS:EDIT')")
    public void delete(@PathVariable Long id) {
        chapterService.delete(id);
    }
}
