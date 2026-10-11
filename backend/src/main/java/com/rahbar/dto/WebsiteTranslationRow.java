package com.rahbar.dto;

import java.time.LocalDateTime;

public record WebsiteTranslationRow(String lang, String field, String value, String changedBy, LocalDateTime changedAt) {
}
