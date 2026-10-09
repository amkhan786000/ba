package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** What a graduated student does now (Admin > Alumni; graduates update their own). */
@Entity
@Table(name = "alumni_profiles")
@Getter @Setter
public class AlumniProfile extends Modifiable {
    /** EMPLOYED, HIGHER_STUDIES, SELF_EMPLOYED, SEEKING_WORK or OTHER. */
    public static final java.util.Map<String, String> STATUSES = new java.util.LinkedHashMap<>();
    static {
        STATUSES.put("EMPLOYED", "Employed");
        STATUSES.put("HIGHER_STUDIES", "Higher studies");
        STATUSES.put("SELF_EMPLOYED", "Self-employed / business");
        STATUSES.put("SEEKING_WORK", "Looking for work");
        STATUSES.put("OTHER", "Other");
    }

    /** users.id of the graduate. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_status", length = 30)
    private String currentStatus;

    /** Employer, university or business. */
    @Column(name = "organisation", length = 200)
    private String organisation;

    /** Job title or course of study. */
    @Column(name = "role_title", length = 200)
    private String roleTitle;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    /** May Rahbar contact them (mentoring, events, giving back)? */
    @Column(name = "consent_to_contact", nullable = false)
    private Boolean consentToContact = false;

    @Column(name = "notes", length = 2000)
    private String notes;
}
