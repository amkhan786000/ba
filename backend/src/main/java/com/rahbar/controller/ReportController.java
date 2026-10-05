package com.rahbar.controller;

import com.rahbar.service.ReportCatalogService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Admin Reports page: the catalog of reports and their CSV / Excel / PDF downloads. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAnyRole('1','2')")
public class ReportController {

    private final ReportCatalogService reportCatalogService;

    public ReportController(ReportCatalogService reportCatalogService) {
        this.reportCatalogService = reportCatalogService;
    }

    @GetMapping
    public List<ReportCatalogService.Definition> catalog() {
        return reportCatalogService.catalog();
    }

    /** format = csv (default) | excel | pdf; from / to (yyyy-MM-dd) only apply to reports with a date. */
    @GetMapping("/{type}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String type,
                                                      @RequestParam(defaultValue = "csv") String format,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Downloads.of(reportCatalogService.build(type, format, from, to));
    }
}
