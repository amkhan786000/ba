package com.rahbar.dto;

import com.rahbar.entity.WebGalleryImage;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WebsiteImageView(Long id, String title, String caption, String album, LocalDate takenOn, int displayOrder,
                        boolean published, String url, String contentType, long sizeBytes, LocalDateTime createdAt) {
    public static WebsiteImageView of(WebGalleryImage g) {
        return new WebsiteImageView(g.getId(), g.getTitle(), g.getCaption(), g.getAlbum(), g.getTakenOn(), g.getDisplayOrder(),
                g.isPublished(), g.getUrl(), g.getContentType(), g.getSizeBytes(), g.getCreatedAt());
    }
}
