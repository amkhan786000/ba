package com.rahbar.dto;

import com.rahbar.entity.WebMessage;

public record WebsiteContactRequest(String name, String email, String phone, WebMessage.Topic topic, String subject,
                             String message, String website) {
}
