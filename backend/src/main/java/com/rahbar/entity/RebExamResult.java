package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A girl's result in an REB exam as published, and her class teacher's remark. */
@Entity
@Table(name = "reb_exam_results")
@Getter @Setter
public class RebExamResult extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long examId;

    @Column(nullable = false)
    private Long studentId;

    private Long sectionId;

    @Column(precision = 8, scale = 2)
    private BigDecimal madrasaTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal madrasaMax;

    @Column(precision = 8, scale = 2)
    private BigDecimal schoolTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal schoolMax;

    @Column(precision = 8, scale = 2)
    private BigDecimal total;

    @Column(precision = 8, scale = 2)
    private BigDecimal maxTotal;

    @Column(precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(length = 5)
    private String grade;

    private Integer rankInSection;

    /** PASS, FAIL or ABSENT. */
    @Column(length = 10)
    private String result;

    @Column(precision = 5, scale = 2)
    private BigDecimal attendancePercent;

    @Column(length = 500)
    private String teacherRemark;

    private boolean published;
}
