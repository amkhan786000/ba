package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "application_status")
@Getter @Setter
public class ApplicationStatus extends Modifiable {
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

}
