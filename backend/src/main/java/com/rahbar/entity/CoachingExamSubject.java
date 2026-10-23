package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A subject of an exam and its maximum marks. */
@Entity
@Table(name = "coaching_exam_subjects")
@Getter @Setter
public class CoachingExamSubject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "max_marks", nullable = false)
    private BigDecimal maxMarks;
}
