package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One installment paid in a sponsor's online payment, and when the office passed the money on to the student. */
@Entity
@Table(name = "online_payment_items")
@Getter @Setter
public class OnlinePaymentItem extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long onlinePaymentId;

    @Column(nullable = false)
    private Long installmentId;

    @Column(nullable = false)
    private Long studentUserId;

    @Column(nullable = false)
    private BigDecimal amount;

    /** The payments row made when it was paid. */
    private Long paymentId;

    private LocalDate passedOnAt;

    @Column(length = 100)
    private String passedOnRef;

    private Long passedOnBy;
}
