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

    /** The scholarship application (scholarship_applications.id). Rows from before V45 have grantee_detail_id instead. */
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "status")
    private String status = "draft";

    @Column(name = "comments")
    private String comments;

}
