package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An REB test or term exam of a session. */
@Entity
@Table(name = "reb_exams")
@Getter @Setter
public class RebExam extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false, length = 100)
    private String name;

    /** UNIT_TEST, TERM, ANNUAL or ORAL. */
    @Column(nullable = false, length = 10)
    private String kind = "TERM";

    /** Share in the annual result (all exams of a session with a weight are combined). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent = BigDecimal.ZERO;

    private LocalDate startDate;

    private LocalDate endDate;

    /** DRAFT, MARKS_ENTRY or PUBLISHED. */
    @Column(nullable = false, length = 12)
    private String status = "DRAFT";

    private java.time.LocalDateTime publishedAt;
}
