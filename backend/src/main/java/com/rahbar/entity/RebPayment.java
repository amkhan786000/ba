package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Money received for an REB girl, with its receipt number. Cancelled, never deleted. */
@Entity
@Table(name = "reb_payments")
@Getter @Setter
public class RebPayment extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String receiptNo;

    @Column(nullable = false)
    private Long studentId;

    /** FAMILY or SPONSOR. */
    @Column(nullable = false, length = 10)
    private String payer = "FAMILY";

    private Long sponsorUserId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private java.time.LocalDate paidOn;

    /** CASH, UPI, BANK or CHEQUE. */
    @Column(nullable = false, length = 10)
    private String method = "CASH";

    /** UPI / bank reference or cheque number. */
    @Column(length = 100)
    private String reference;

    @Column(length = 500)
    private String note;

    /** VALID or CANCELLED. */
    @Column(nullable = false, length = 10)
    private String status = "VALID";

    @Column(length = 500)
    private String cancelReason;
}
