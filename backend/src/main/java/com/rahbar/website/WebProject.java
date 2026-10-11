package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A project shown on the website (Admin > Website > Projects). */
@Entity
@Table(name = "web_projects")
@Getter @Setter
public class WebProject extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String slug;
    private String category;
    @Column(nullable = false, length = 600)
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String sponsorshipInfo;
    private String imageUrl;
    private String websiteUrl;
    /** Name of a gallery album whose photos are shown on the project page. */
    @Column(length = 80)
    private String galleryAlbum;
    private int displayOrder;
    private boolean featured;
    private boolean active = true;
}
