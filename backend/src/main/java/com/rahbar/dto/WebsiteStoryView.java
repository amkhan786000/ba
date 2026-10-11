package com.rahbar.dto;

import com.rahbar.entity.WebStory;
import java.time.LocalDateTime;

/** Privacy settings applied: first name and initial unless the full name may be shown; the photo only if allowed. */
public record WebsiteStoryView(Long id, String slug, String title, String name, String photoUrl, String summary, String story,
                        String nowWhere, Integer year, Long projectId, Long chapterId, Long coachingCenterId,
                        LocalDateTime publishedAt) {
    public static WebsiteStoryView of(WebStory s) {
        return new WebsiteStoryView(s.getId(), s.getSlug(), s.getTitle(), s.publicName(), s.isShowPhoto() ? s.getPhotoUrl() : null,
                s.getSummary(), s.getStory(), s.getNowWhere(), s.getYear(), s.getProjectId(), s.getChapterId(),
                s.getRccCenterId(), s.getPublishedAt());
    }
}
