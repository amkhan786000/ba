package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A student's marks in one subject of an exam. */
@Entity
@Table(name = "coaching_exam_marks")
@Getter @Setter
public class CoachingExamMark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "marks", nullable = false)
    private BigDecimal marks;
}
