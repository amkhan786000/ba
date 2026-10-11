package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A reduction of a girl's fees in a session (PERCENT or AMOUNT per bill; one head or all heads). */
@Entity
@Table(name = "reb_concessions")
@Getter @Setter
public class RebConcession extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long sessionId;

    /** Empty: every head. */
    private Long feeHeadId;

    /** PERCENT or AMOUNT. */
    @Column(nullable = false, length = 10)
    private String kind = "PERCENT";

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** ORPHAN, SIBLING, STAFF_CHILD, NEED or OTHER. */
    @Column(nullable = false, length = 20)
    private String reason = "NEED";

    @Column(length = 500)
    private String note;

    /** YYYY-MM, empty: from the start of the session. */
    @Column(length = 7)
    private String fromPeriod;

    /** YYYY-MM, empty: to the end of the session. */
    @Column(length = 7)
    private String toPeriod;
}
