package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "approvals")
@Getter @Setter
public class Approval extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(name = "status")
    private String status = "Pending";

    @Column(name = "comments")
    private String comments;

}
