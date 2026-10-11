package com.rahbar.dto;

import java.time.LocalDateTime;

public record WebsiteContentVersion(Long id, String value, String savedBy, LocalDateTime savedAt) {
}
