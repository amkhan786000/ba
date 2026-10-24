package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A job, internship or training opening posted for students and graduates (Careers > Opportunities). */
@Entity
@Table(name = "career_opportunities")
@Getter @Setter
public class CareerOpportunity extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** JOB, INTERNSHIP or TRAINING. */
    @Column(name = "kind", nullable = false, length = 12)
    private String kind;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "organisation", nullable = false, length = 200)
    private String organisation;

    @Column(name = "employer_id")
    private Long employerId;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "description", length = 3000)
    private String description;

    @Column(name = "eligibility", length = 500)
    private String eligibility;

    @Column(name = "apply_link", length = 500)
    private String applyLink;

    /** Apply by. */
    @Column(name = "last_date")
    private java.time.LocalDate lastDate;

    /** OPEN or CLOSED. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "OPEN";
}
