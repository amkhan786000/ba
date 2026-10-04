package com.rahbar.controller;

import com.rahbar.service.ReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Turns a built report into a file-download response. */
final class Downloads {
    private Downloads() {}

    static ResponseEntity<ByteArrayResource> of(ReportService.Report report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.filename() + "\"")
                .body(new ByteArrayResource(report.bytes()));
    }
}
