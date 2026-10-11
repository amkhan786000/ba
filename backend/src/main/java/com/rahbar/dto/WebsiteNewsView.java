package com.rahbar.dto;

import com.rahbar.entity.WebNews;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WebsiteNewsView(Long id, String title, String slug, WebNews.Kind kind, String summary, String content,
                       LocalDate eventDate, String location, Long chapterId, String imageUrl, boolean published,
                       LocalDateTime createdAt) {
    public static WebsiteNewsView of(WebNews n) {
        return new WebsiteNewsView(n.getId(), n.getTitle(), n.getSlug(), n.getKind(), n.getSummary(), n.getContent(),
                n.getEventDate(), n.getLocation(), n.getChapterId(), n.getImageUrl(), n.isPublished(), n.getCreatedAt());
    }
}
