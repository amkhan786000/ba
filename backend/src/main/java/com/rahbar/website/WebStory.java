package com.rahbar.website;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
/**
 * A success story. The website shows only the first name and initial unless showFullName, and the photo only if
 * showPhoto; consent is required. Chapter / RCC-scoped users manage only their own chapter's / center's.
 */
@Entity
@Table(name = "web_stories")
@Getter @Setter
public class WebStory extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, unique = true, length = 200)
    private String slug;
    @Column(nullable = false, length = 120)
    private String personName;
    private boolean showFullName;
    private boolean showPhoto = true;
    private boolean consentGiven;
    private String photoUrl;
    @Column(length = 600)
    private String summary;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String story;
    @Column(length = 200)
    private String nowWhere;
    @Column(name = "story_year")
    private Integer year;
    private Long projectId;
    private Long chapterId;
    private Long rccCenterId;
    private boolean reviewRequested;
    private boolean published;
    private LocalDateTime publishedAt;

    @PrePersist
    @PreUpdate
    void stampPublication() {
        if (published && publishedAt == null) publishedAt = LocalDateTime.now();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getStatus() {
        return published ? "PUBLISHED" : reviewRequested ? "IN_REVIEW" : "DRAFT";
    }

    /** "Sana Parveen Khatoon" becomes "Sana K." unless the full name may be shown. */
    public String publicName() {
        if (showFullName || personName == null) return personName;
        String[] parts = personName.strip().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
