package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One online payment through Razorpay: a sponsor paying installments, or a donation from the website. */
@Entity
@Table(name = "online_payments")
@Getter @Setter
public class OnlinePayment extends Modifiable {

    public static final String SPONSOR = "SPONSOR";
    public static final String DONATION = "DONATION";
    public static final String CREATED = "CREATED";
    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String purpose;

    @Column(nullable = false, length = 10)
    private String status = CREATED;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String gateway = "RAZORPAY";

    @Column(length = 60)
    private String gatewayOrderId;

    @Column(length = 60)
    private String gatewayPaymentId;

    private Long sponsorUserId;

    @Column(nullable = false, length = 150)
    private String donorName;

    @Column(length = 150)
    private String donorEmail;

    @Column(length = 30)
    private String donorPhone;

    @Column(length = 10)
    private String donorPan;

    @Column(length = 500)
    private String donorAddress;

    /** Donations: what it is for. */
    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private boolean citizenDeclared;

    @Column(length = 30)
    private String receiptNo;

    private LocalDateTime paidAt;
}
