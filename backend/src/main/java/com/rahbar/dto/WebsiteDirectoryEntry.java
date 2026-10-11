package com.rahbar.dto;

import com.rahbar.entity.WebMember;

/** Contact details only when the member allowed it. */
public record WebsiteDirectoryEntry(Long id, String fullName, WebMember.HomeState homeState, String homeDistrict,
                             String currentCountry, String currentCity, String profession, String organization,
                             String email, String phone) {
    public static WebsiteDirectoryEntry of(WebMember m) {
        return new WebsiteDirectoryEntry(m.getId(), m.getFullName(), m.getHomeState(), m.getHomeDistrict(), m.getCurrentCountry(),
                m.getCurrentCity(), m.getProfession(), m.getOrganization(),
                m.isShowContact() ? m.getEmail() : null, m.isShowContact() ? m.getPhone() : null);
    }
}
