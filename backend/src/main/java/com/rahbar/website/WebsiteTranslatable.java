package com.rahbar.website;

import com.rahbar.security.Section;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Records whose fields have Urdu / Hindi versions, and the section whose EDIT permission allows changing them. */
public enum WebsiteTranslatable {
    PROJECT(Section.WEBSITE_PROJECTS, List.of("name", "category", "summary", "description", "sponsorshipInfo")),
    NEWS(Section.WEBSITE_NEWS, List.of("title", "summary", "content", "location")),
    PAGE(Section.WEBSITE_PAGES, List.of("title", "body")),
    STORY(Section.WEBSITE_STORIES, List.of("title", "summary", "story", "nowWhere")),
    CATEGORY(Section.WEBSITE_BLOG_CATEGORIES, List.of("name", "description"));

    private final Section section;
    private final List<String> fields;

    WebsiteTranslatable(Section section, List<String> fields) {
        this.section = section;
        this.fields = fields;
    }

    public Section section() {
        return section;
    }

    public List<String> fields() {
        return fields;
    }

    public static Optional<WebsiteTranslatable> of(String type) {
        return Arrays.stream(values()).filter(t -> t.name().equalsIgnoreCase(type == null ? "" : type)).findFirst();
    }
}
