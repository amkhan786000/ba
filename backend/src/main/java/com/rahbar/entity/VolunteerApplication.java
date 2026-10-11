package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Someone who offered to help through the public website: mentor, tutor, sponsor and / or join a chapter.
 * Staff follow them up in Admin > Volunteers (status and internal notes).
 */
@Entity
@Table(name = "volunteer_applications")
@Getter @Setter
public class VolunteerApplication extends Modifiable {

    /** Status code -> label, in display order. */
    public static final Map<String, String> STATUSES = new LinkedHashMap<>();
    static {
        STATUSES.put("NEW", "New");
        STATUSES.put("CONTACTED", "Contacted");
        STATUSES.put("ONBOARDED", "Onboarded");
        STATUSES.put("DECLINED", "Declined");
    }

    /** How a tutor can teach. */
    public static final Map<String, String> TUTOR_MODES = new LinkedHashMap<>();
    static {
        TUTOR_MODES.put("ONLINE", "Online");
        TUTOR_MODES.put("IN_PERSON", "In person");
        TUTOR_MODES.put("EITHER", "Online or in person");
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "volunteer_id")
    private Long volunteerId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "home_district", length = 100)
    private String homeDistrict;

    @Column(name = "profession", length = 150)
    private String profession;

    @Column(name = "wants_mentor", nullable = false)
    private boolean wantsMentor;

    @Column(name = "wants_tutor", nullable = false)
    private boolean wantsTutor;

    @Column(name = "wants_sponsor", nullable = false)
    private boolean wantsSponsor;

    @Column(name = "wants_chapter", nullable = false)
    private boolean wantsChapter;

    /** Comma-separated fields of expertise. */
    @Column(name = "mentor_fields", length = 500)
    private String mentorFields;

    /** Comma-separated subjects. */
    @Column(name = "tutor_subjects", length = 500)
    private String tutorSubjects;

    /** Comma-separated grades, e.g. "8,9,10". */
    @Column(name = "tutor_grades", length = 200)
    private String tutorGrades;

    /** ONLINE, IN_PERSON or EITHER (see TUTOR_MODES). */
    @Column(name = "tutor_mode", length = 20)
    private String tutorMode;

    /** RCC center a tutor would like to teach at (optional). */
    @Column(name = "rcc_center_id")
    private Long rccCenterId;

    @Column(name = "hours_per_month")
    private Integer hoursPerMonth;

    /** How many students a sponsor would like to support. */
    @Column(name = "sponsor_students")
    private Integer sponsorStudents;

    /** Chapter to join; empty = no chapter in their district yet (they may help start one). */
    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "message", length = 2000)
    private String message;

    /** Where the sign-up came from, e.g. "biharanjuman.com". */
    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "NEW";

    /** Internal staff notes; never shown to the volunteer. */
    @Column(name = "notes", length = 2000)
    private String notes;
}
