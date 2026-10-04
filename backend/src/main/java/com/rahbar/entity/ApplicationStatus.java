package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "application_status")
@Getter @Setter
public class ApplicationStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "grantee_detail_id", nullable = false)
    private Long granteeDetailId;

    @Column(name = "status")
    private String status = "draft";

    @Column(name = "comments")
    private String comments;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
