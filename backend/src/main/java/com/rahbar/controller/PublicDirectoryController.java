package com.rahbar.controller;

import com.rahbar.service.PublicDirectoryService;
import com.rahbar.service.PublicDirectoryService.PublicCenter;
import com.rahbar.service.PublicDirectoryService.PublicChapter;
import com.rahbar.service.PublicDirectoryService.PublicStats;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Read-only lists for public websites such as biharanjuman.com, which show Rahbar's chapters and
 * RCC centers (and the number of students) without anyone signing in. /api/public/** is open (SecurityConfig).
 */
@RestController
@RequestMapping("/api/public")
public class PublicDirectoryController {

    private final PublicDirectoryService directory;

    public PublicDirectoryController(PublicDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/chapters")
    public ResponseEntity<List<PublicChapter>> chapters() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(directory.chapters());
    }

    @GetMapping("/rcc-centers")
    public ResponseEntity<List<PublicCenter>> centers() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(directory.centers());
    }

    @GetMapping("/stats")
    public ResponseEntity<PublicStats> stats() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(directory.stats());
    }
}
