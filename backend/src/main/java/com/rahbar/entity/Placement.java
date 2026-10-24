package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Where a student or graduate got placed: a job, internship, higher studies or their own work. */
@Entity
@Table(name = "placements")
@Getter @Setter
public class Placement extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** JOB, INTERNSHIP, HIGHER_STUDIES or SELF_EMPLOYED. */
    @Column(name = "kind", nullable = false, length = 15)
    private String kind;

    @Column(name = "organisation", nullable = false, length = 200)
    private String organisation;

    @Column(name = "employer_id")
    private Long employerId;

    @Column(name = "role_title", length = 150)
    private String roleTitle;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    /** Optional, e.g. "1-2 lakh a year". */
    @Column(name = "salary_band", length = 30)
    private String salaryBand;

    @Column(name = "opportunity_id")
    private Long opportunityId;

    @Column(name = "added_by_student", nullable = false)
    private boolean addedByStudent = false;

    /** When the office confirmed it (null: not confirmed yet). */
    @Column(name = "verified_at")
    private java.time.LocalDateTime verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "note", length = 500)
    private String note;
}
