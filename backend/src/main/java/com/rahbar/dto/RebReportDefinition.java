package com.rahbar.dto;

/** One REB report on the REB Reports screen. dateRange: the report takes an optional from / to date. */
public record RebReportDefinition(String key, String category, String title, String description, boolean dateRange) {}
