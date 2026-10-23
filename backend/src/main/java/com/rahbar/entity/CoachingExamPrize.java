package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A 1st, 2nd or 3rd prize of an exam (equal totals share the position). */
@Entity
@Table(name = "coaching_exam_prizes")
@Getter @Setter
public class CoachingExamPrize {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "total", nullable = false)
    private BigDecimal total;
}
