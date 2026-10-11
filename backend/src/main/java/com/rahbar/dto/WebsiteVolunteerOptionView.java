package com.rahbar.dto;

import java.util.List;

/** A way to help on the volunteer form, with its lists split into items (in the visitor's language). */
public record WebsiteVolunteerOptionView(String key, String kind, String title, String description,
                                  String choicesLabel, List<String> choices, String extraLabel, List<String> extraChoices) {
}
