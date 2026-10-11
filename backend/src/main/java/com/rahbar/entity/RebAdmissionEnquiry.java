package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An enquiry about admitting a girl to REB (from the website, a phone call or a visit). */
@Entity
@Table(name = "reb_admission_enquiries")
@Getter @Setter
public class RebAdmissionEnquiry extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String girlName;

    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 120)
    private String guardianName;

    @Column(length = 20)
    private String relation;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 80)
    private String district;

    @Column(length = 40)
    private String state;

    @Column(length = 80)
    private String classSought;

    @Column(length = 150)
    private String previousSchool;

    private boolean residential = true;

    @Column(length = 2000)
    private String message;

    /** WEBSITE, PHONE or WALK_IN. */
    @Column(nullable = false, length = 10)
    private String source = "WEBSITE";

    /** NEW, CONTACTED, TEST, ADMITTED or NOT_ADMITTED. */
    @Column(nullable = false, length = 12)
    private String status = "NEW";

    /** Internal notes of the office. */
    @Column(length = 2000)
    private String notes;

    /** The girl admitted from this enquiry. */
    private Long studentId;
}
