package com.rahbar.dto;

/** A Knowledge Hub section as the website shows it: name and address, plus how many posts it has. */
public record WebsiteCategoryView(Long id, String slug, String name, String description, long posts) {
}
