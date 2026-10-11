package com.rahbar.dto;

/** One entry of the catalog shown on the Reports page. */
public record ReportDefinition(String key, String category, String title, String description, boolean dateRange) {}
