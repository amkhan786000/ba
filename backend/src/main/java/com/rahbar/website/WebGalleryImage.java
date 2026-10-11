package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
/** A photo; the file is kept by WebsiteImageStorage. Unpublished photos (blog / story photos) stay out of the gallery. */
@Entity
@Table(name = "web_gallery_images")
@Getter @Setter
public class WebGalleryImage extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 1000)
    private String caption;
    @Column(length = 80)
    private String album;
    private LocalDate takenOn;
    private int displayOrder;
    private boolean published = true;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(nullable = false, length = 64)
    private String fileName;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(length = 40)
    private String contentType;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long sizeBytes;

    /** Works on the website (its /api/ is forwarded to /api/public/website/) and in this app. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getUrl() {
        return fileName == null ? null : "/api/images/" + fileName;
    }
}
