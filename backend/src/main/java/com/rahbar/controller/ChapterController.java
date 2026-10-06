package com.rahbar.controller;

import com.rahbar.entity.Chapter;
import com.rahbar.service.ChapterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chapters (Super Admin / Application Administrator). Feeds the Chapter dropdown on the Add User form. */
@RestController
@RequestMapping("/api/admin/chapters")
@PreAuthorize("hasAnyRole('1','2')")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping
    public List<Chapter> list() {
        return chapterService.list();
    }

    @PostMapping
    public Chapter save(@RequestBody Chapter chapter) {
        return chapterService.save(chapter);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        chapterService.delete(id);
    }
}
