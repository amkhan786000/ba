package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A girl at REB. Not a Rahbar account; rahbar_user_id links her if she later joins the scholarship. */
@Entity
@Table(name = "reb_students")
@Getter @Setter
public class RebStudent extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String admissionNo;
    @Column(nullable = false, length = 120)
    private String name;
    private java.time.LocalDate dateOfBirth;
    /** Only the last 4 digits; the full number exists only in an uploaded document. */
    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;
    @Column(length = 255)
    private String photoPath;
    @Column(length = 5)
    private String bloodGroup;
    @Column(length = 500)
    private String address;
    @Column(length = 80)
    private String district;
    @Column(length = 40)
    private String state;
    /** GEN, EWS, BC, EBC, SC or ST (Bihar Board forms). */
    @Column(length = 10)
    private String socialCategory;
    @Column(length = 40)
    private String boardRegistrationNo;
    @Column(length = 150)
    private String previousSchool;
    private boolean residential = true;
    /** SELF, SPONSORED or FREE. */
    @Column(nullable = false, length = 10)
    private String feePlan = "SELF";
    /** ACTIVE, LEFT or PASSED_OUT. */
    @Column(nullable = false, length = 12)
    private String status = "ACTIVE";
    private java.time.LocalDate admittedOn;
    private java.time.LocalDate leftOn;
    @Column(length = 500)
    private String leavingReason;
    private Long rahbarUserId;
    @Column(length = 2000)
    private String notes;
}
