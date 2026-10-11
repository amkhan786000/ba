package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Money in or out of the organisation (besides sponsor payments and REB fees, which Rahbar records elsewhere). */
@Entity
@Table(name = "fin_entries")
@Getter @Setter
public class FinEntry extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private java.time.LocalDate entryDate;

    /** INCOME or EXPENSE. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false)
    private Long categoryId;

    /** The website project it belongs to ("where the money went"); empty: the organisation in general. */
    private Long projectId;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal amount;

    /** Who paid / who was paid. */
    @Column(length = 150)
    private String party;

    @Column(length = 500)
    private String description;

    /** CASH, UPI, BANK or CHEQUE. */
    @Column(nullable = false, length = 10)
    private String method = "BANK";

    @Column(length = 100)
    private String reference;
}
