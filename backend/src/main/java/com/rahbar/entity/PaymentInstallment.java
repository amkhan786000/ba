package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One installment a sponsor owes a student, built from the student's course (semesters), session year
 * (Payment Config amount and frequency) and payment start date. Paid once linked to a Paid payment;
 * otherwise Due once due_date has passed, else Not Due (see service.PaymentInstallmentService).
 */
@Entity
@Table(name = "payment_installments")
@Getter @Setter
public class PaymentInstallment extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id")
    private Long installmentId;

    /** users.id of the student. */
    @Column(name = "grantee_id", nullable = false)
    private Long granteeId;

    /** users.id of the sponsor (for paid rows, the sponsor who paid). */
    @Column(name = "grantor_id", nullable = false)
    private Long grantorId;

    /** 1, 2, 3, ... in due-date order. */
    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** The payment that paid this installment, if any. */
    @Column(name = "payment_id", unique = true)
    private Long paymentId;
}
