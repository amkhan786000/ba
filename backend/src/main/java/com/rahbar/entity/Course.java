package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "courses")
@Getter @Setter
public class Course extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "institution_id", nullable = false, length = 50)
    private String institutionId;

    /** What every page shows; can be edited. */
    @Column(name = "course_name", nullable = false)
    private String courseName;

    /** A short code: set when the course is added (default: its name), then kept as it is when the name changes. */
    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "course_description")
    private String courseDescription;

    @Column(name = "fees_per_semester", nullable = false)
    private BigDecimal feesPerSemester;

    @Column(name = "number_of_semesters", nullable = false)
    private Integer numberOfSemesters;
}
