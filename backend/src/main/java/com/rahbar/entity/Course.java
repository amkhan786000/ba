package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "courses")
@Getter @Setter
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "institution_id", nullable = false, length = 50)
    private String institutionId;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "course_description")
    private String courseDescription;

    @Column(name = "fees_per_semester", nullable = false)
    private BigDecimal feesPerSemester;

    @Column(name = "number_of_semesters", nullable = false)
    private Integer numberOfSemesters;
}
