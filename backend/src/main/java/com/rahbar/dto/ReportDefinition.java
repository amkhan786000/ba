package com.rahbar.dto;

/**
 * One entry of the catalog shown on the Reports page. {@code dateRange}: the From / To days (and the Year, as 1 Jan to
 * 31 Dec) apply; {@code sessionYear}: the Year picks students of that session year.
 */
public record ReportDefinition(String key, String category, String title, String description, boolean dateRange, boolean sessionYear) {

    public ReportDefinition(String key, String category, String title, String description, boolean dateRange) {
        this(key, category, title, description, dateRange, false);
    }

    public ReportDefinition withSessionYear() {
        return new ReportDefinition(key, category, title, description, dateRange, true);
    }
}
