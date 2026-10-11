package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A girl's marks in an exam paper. */
@Entity
@Table(name = "reb_marks")
@Getter @Setter
public class RebMark extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paperId;

    @Column(nullable = false)
    private Long studentId;

    @Column(precision = 6, scale = 2)
    private BigDecimal marks;

    private boolean absent;

    @Column(length = 200)
    private String remark;
}
