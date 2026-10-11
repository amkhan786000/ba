package com.rahbar.dto;

import com.rahbar.entity.WebBlogPost;
import java.time.LocalDateTime;
import java.util.Map;

/** The author's email is never shown. */
public record WebsitePostView(Long id, String title, String slug, String topic, String author, String summary, String content,
                       String coverImageUrl, Long projectId, Long categoryId, WebsiteCategoryRef category,
                       String attachmentUrl, String attachmentName, String mediaUrl, String subjectName,
                       String subjectDetails, boolean published, LocalDateTime publishedAt, LocalDateTime createdAt) {
    public static WebsitePostView of(WebBlogPost p) {
        return of(p, Map.of());
    }

    public static WebsitePostView of(WebBlogPost p, Map<Long, WebsiteCategoryRef> categories) {
        return new WebsitePostView(p.getId(), p.getTitle(), p.getSlug(), p.getTopic(), p.getAuthor(), p.getSummary(),
                p.getContent(), p.getCoverImageUrl(), p.getProjectId(), p.getCategoryId(),
                p.getCategoryId() == null ? null : categories.get(p.getCategoryId()), // none after a category was deleted
                p.getAttachmentUrl(), p.getAttachmentName(), p.getMediaUrl(), p.getSubjectName(), p.getSubjectDetails(),
                p.isPublished(), p.getPublishedAt(), p.getCreatedAt());
    }
}
