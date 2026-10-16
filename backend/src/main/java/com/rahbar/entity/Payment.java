package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter
public class Payment extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "grantor_id", nullable = false)
    private Long grantorId;

    @Column(name = "grantee_id", nullable = false)
    private Long granteeId;

    /** What the sponsor paid. Kept, never shown: screens show installments (see util.NotShown). */
    @com.rahbar.util.NotShown @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "student_proof_url")
    private String studentProofUrl;

    @Column(name = "status")
    private String status = "Pending";

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** ONLINE when paid through Rahbar's online payments (Razorpay); null for payments recorded with a receipt. */
    @Column(name = "method", length = 10)
    private String method;

}
