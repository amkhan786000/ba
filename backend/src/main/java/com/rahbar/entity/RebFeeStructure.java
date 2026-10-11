package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** The amount of a fee head for a class in a session. */
@Entity
@Table(name = "reb_fee_structures")
@Getter @Setter
public class RebFeeStructure extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long classId;

    @Column(nullable = false)
    private Long feeHeadId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** 1-12: the month an ANNUAL head is billed. */
    private Integer billMonth;
}
