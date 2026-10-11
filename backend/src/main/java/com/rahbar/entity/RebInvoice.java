package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One REB bill: a girl's fees for a month, split into a sponsor share and a family share. */
@Entity
@Table(name = "reb_invoices")
@Getter @Setter
public class RebInvoice extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String invoiceNo;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long sessionId;

    /** YYYY-MM. */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    private java.time.LocalDate dueDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal gross = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal concession = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal net = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sponsorShare = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal familyShare = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sponsorPaid = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal familyPaid = BigDecimal.ZERO;

    private Long sponsorshipId;

    /** DUE, PARTIAL, PAID, WAIVED or CANCELLED. Overdue: due date passed and not paid. */
    @Column(nullable = false, length = 10)
    private String status = "DUE";

    @Column(length = 500)
    private String note;

    /** What is still to be paid of a share (SPONSOR or FAMILY); zero once waived or cancelled. */
    public BigDecimal outstanding(String share) {
        if ("WAIVED".equals(status) || "CANCELLED".equals(status)) return BigDecimal.ZERO;
        BigDecimal left = "SPONSOR".equals(share) ? sponsorShare.subtract(sponsorPaid) : familyShare.subtract(familyPaid);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }
}
