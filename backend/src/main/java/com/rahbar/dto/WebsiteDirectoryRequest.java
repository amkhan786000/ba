package com.rahbar.dto;

import com.rahbar.entity.WebMember;

public record WebsiteDirectoryRequest(String fullName, WebMember.HomeState homeState, String homeDistrict, String currentCountry,
                               String currentCity, String profession, String organization, String email, String phone,
                               Long chapterId, boolean showContact, String website) {
}
