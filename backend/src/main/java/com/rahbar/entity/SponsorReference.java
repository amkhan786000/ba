package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sponsor_references")
@Getter @Setter
public class SponsorReference {
    @Id
    @Column(name = "reference_id", length = 50)
    private String referenceId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "sponsor_year")
    private String sponsorYear;

    @Column(name = "chapter")
    private String chapter;

    @Column(name = "referral")
    private String referral;

    @Column(name = "installment_date")
    private LocalDate installmentDate;

    @Column(name = "payment_months")
    private Integer paymentMonths = 0;

    @Column(name = "confirm_credit_date")
    private LocalDate confirmCreditDate;

    @Column(name = "special_demand")
    private String specialDemand;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "mobile_1")
    private String mobile1;

    @Column(name = "mobile_2")
    private String mobile2;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
