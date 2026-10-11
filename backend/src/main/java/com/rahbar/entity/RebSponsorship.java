package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A Rahbar sponsor supporting an REB girl every month (e.g. "Educate a girl: Rs. 500 per month"). */
@Entity
@Table(name = "reb_sponsorships")
@Getter @Setter
public class RebSponsorship extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    /** users.id of the sponsor. */
    @Column(nullable = false)
    private Long sponsorUserId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyAmount;

    /** YYYY-MM. */
    @Column(nullable = false, length = 7)
    private String startPeriod;

    /** YYYY-MM, empty: ongoing. */
    @Column(length = 7)
    private String endPeriod;

    /** ACTIVE or ENDED. */
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(length = 500)
    private String note;
}
