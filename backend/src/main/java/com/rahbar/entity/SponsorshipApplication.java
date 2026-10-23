package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A sponsor's application for an application period (Applications > Apply for Sponsorship): how many students they
 * want to sponsor, and what kind (the same preferences as on the sponsor). One per sponsor and period.
 */
@Entity
@Table(name = "sponsorship_applications")
@Getter @Setter
public class SponsorshipApplication extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "sponsor_id", nullable = false)
    private Long sponsorId;

    @Column(name = "students_wanted", nullable = false)
    private Integer studentsWanted;

    /** GOVT, PRIVATE or ANY; null = not said. */
    @Column(name = "college_type", length = 10)
    private String collegeType;

    /** One per line; empty = any. */
    @Column(name = "father_professions", columnDefinition = "TEXT")
    private String fatherProfessions;

    @Column(name = "income_min")
    private BigDecimal incomeMin;

    @Column(name = "income_max")
    private BigDecimal incomeMax;

    @Column(name = "note", length = 500)
    private String note;
}
