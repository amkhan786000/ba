package com.rahbar.controller;

import com.rahbar.service.FileAccessService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uploaded files (payment receipts, spending proofs, progress reports, application documents, broadcast
 * attachments). Only signed-in users, and only the files they may see (see FileAccessService).
 */
@RestController
@RequestMapping("/api/files")
@PreAuthorize("isAuthenticated()")
public class FileController {

    private final FileAccessService fileAccessService;

    public FileController(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
    }

    @GetMapping("/{name:.+}")
    public ResponseEntity<FileSystemResource> open(@PathVariable String name) throws Exception {
        Path path = fileAccessService.requireReadable(name);
        String type = Files.probeContentType(path);
        return ResponseEntity.ok()
                .contentType(type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(path.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(path));
    }
}
