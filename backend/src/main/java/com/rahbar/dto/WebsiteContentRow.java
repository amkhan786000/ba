package com.rahbar.dto;

import java.time.LocalDateTime;

public record WebsiteContentRow(String key, String lang, String value, String changedBy, LocalDateTime changedAt) {
}
