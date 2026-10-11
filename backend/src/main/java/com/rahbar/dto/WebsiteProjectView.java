package com.rahbar.dto;

import com.rahbar.entity.WebProject;

public record WebsiteProjectView(Long id, String name, String slug, String category, String summary, String description,
                          String sponsorshipInfo, String imageUrl, String websiteUrl, String galleryAlbum,
                          int displayOrder, boolean featured, boolean active) {
    public static WebsiteProjectView of(WebProject p) {
        return new WebsiteProjectView(p.getId(), p.getName(), p.getSlug(), p.getCategory(), p.getSummary(), p.getDescription(),
                p.getSponsorshipInfo(), p.getImageUrl(), p.getWebsiteUrl(), p.getGalleryAlbum(), p.getDisplayOrder(),
                p.isFeatured(), p.isActive());
    }
}
