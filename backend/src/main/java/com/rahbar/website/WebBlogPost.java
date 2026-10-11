package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
/**
 * A blog post. Staff write them in Admin > Website > Blog; visitors send them through "Write for us" (source PUBLIC,
 * author email set, waiting for review). Only WEBSITE_BLOG_PUBLISH may publish.
 */
@Entity
@Table(name = "web_blog_posts")
@Getter @Setter
public class WebBlogPost extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Source { STAFF, PUBLIC }

    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, unique = true, length = 200)
    private String slug;
    @Column(nullable = false, length = 80)
    private String topic;
    @Column(length = 120)
    private String author;
    /** Email of a visitor who sent the post (never shown on the website). */
    @Column(length = 160)
    private String authorEmail;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source = Source.STAFF;
    @Column(length = 600)
    private String summary;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
    private String coverImageUrl;
    private Long projectId;
    private boolean reviewRequested;
    private boolean published;
    private LocalDateTime publishedAt;

    @PrePersist
    @PreUpdate
    void stampPublication() {
        if (published && publishedAt == null) publishedAt = LocalDateTime.now();
    }

    /** PUBLISHED, IN_REVIEW or DRAFT. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getStatus() {
        return published ? "PUBLISHED" : reviewRequested ? "IN_REVIEW" : "DRAFT";
    }
}
