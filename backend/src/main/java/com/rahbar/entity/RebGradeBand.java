package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A band of the grading scale of an REB session. */
@Entity
@Table(name = "reb_grade_bands")
@Getter @Setter
public class RebGradeBand extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercent;

    @Column(nullable = false, length = 5)
    private String grade;

    @Column(length = 60)
    private String remark;

    @Column(name = "is_pass")
    private boolean pass = true;
}
