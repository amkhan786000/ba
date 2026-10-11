package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** How much of a payment went to a bill (share: SPONSOR or FAMILY). */
@Entity
@Table(name = "reb_payment_allocations")
@Getter @Setter
public class RebPaymentAllocation extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long invoiceId;

    @Column(nullable = false, length = 10)
    private String share;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
