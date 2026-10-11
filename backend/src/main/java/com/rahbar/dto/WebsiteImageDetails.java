package com.rahbar.dto;

import java.time.LocalDate;

public record WebsiteImageDetails(String title, String caption, String album, LocalDate takenOn, Integer displayOrder, boolean published) {
}
