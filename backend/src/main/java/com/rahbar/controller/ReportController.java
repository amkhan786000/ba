package com.rahbar.controller;

import com.rahbar.service.ReportCatalogService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Admin Reports page: the catalog of reports, viewing a report on screen and its CSV / Excel / PDF downloads. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class ReportController {

    private final ReportCatalogService reportCatalogService;

    public ReportController(ReportCatalogService reportCatalogService) {
        this.reportCatalogService = reportCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public List<ReportCatalogService.Definition> catalog() {
        return reportCatalogService.catalog();
    }

    /** The report's rows to view on screen: { key, title, columns, rows }. */
    @GetMapping("/{type}/data")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public java.util.Map<String, Object> view(@PathVariable String type,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportCatalogService.data(type, from, to);
    }

    /** format = csv (default) | excel | pdf; from / to (yyyy-MM-dd) only apply to reports with a date. */
    @GetMapping("/{type}")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String type,
                                                      @RequestParam(defaultValue = "csv") String format,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Downloads.of(reportCatalogService.build(type, format, from, to));
    }
}
