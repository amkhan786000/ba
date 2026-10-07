package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bank_details")
@Getter @Setter
public class BankDetails extends Modifiable {
    @Id
    // Assigned as MAX + 1 by the services (see the repository nextId())
    @Column(name = "bank_detail_id")
    private Long bankDetailId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "ifsc_code", nullable = false)
    private String ifscCode;

}
