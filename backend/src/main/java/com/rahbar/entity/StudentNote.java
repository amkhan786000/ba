package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A short update or thank-you note from a student to their sponsor; the office approves it first. */
@Entity
@Table(name = "student_notes")
@Getter @Setter
public class StudentNote extends Modifiable {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String RETURNED = "RETURNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentUserId;

    /** The student's sponsor when they wrote it. */
    @Column(nullable = false)
    private Long sponsorUserId;

    /** UPDATE or THANK_YOU. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    /** Why it was returned (shown to the student). */
    @Column(length = 500)
    private String reviewNote;
}
