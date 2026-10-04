package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "approvals")
@Getter @Setter
public class Approval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "approver_id", nullable = false, length = 50)
    private String approverId;

    @Column(name = "status")
    private String status = "Pending";

    @Column(name = "comments")
    private String comments;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
