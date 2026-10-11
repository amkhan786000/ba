package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A volunteer or a graduate mentoring / tutoring a scholarship student (Students > Mentoring). */
@Entity
@Table(name = "mentorships")
@Getter @Setter
public class Mentorship extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentUserId;

    /** Set when the mentor is a website volunteer. */
    private Long volunteerId;

    /** Set when the mentor is a graduate. */
    private Long alumniUserId;

    /** MENTOR or TUTOR. */
    @Column(nullable = false, length = 10)
    private String help;

    /** What they work on, e.g. "Career in software" or "Physics, Class 12". */
    @Column(length = 200)
    private String focus;

    /** ACTIVE or ENDED. */
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(nullable = false)
    private LocalDate startedOn;

    private LocalDate endedOn;

    @Column(length = 1000)
    private String notes;
}
