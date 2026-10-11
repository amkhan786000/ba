package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A kind of REB fee (Tuition, Hostel, Food, Exam, Admission, ...). */
@Entity
@Table(name = "reb_fee_heads")
@Getter @Setter
public class RebFeeHead extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** MONTHLY (every bill), ANNUAL (once a session, in the structure's bill month) or ONE_TIME (first bill only). */
    @Column(nullable = false, length = 10)
    private String frequency = "MONTHLY";

    /** Only for girls who live in the hostel. */
    private boolean residentialOnly;

    private int displayOrder;

    private boolean active = true;
}
