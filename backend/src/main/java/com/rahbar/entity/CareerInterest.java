package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A student or graduate interested in an opening, and how it went. */
@Entity
@Table(name = "career_interests")
@Getter @Setter
public class CareerInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** INTERESTED, APPLIED, SELECTED or NOT_SELECTED. */
    @Column(name = "status", nullable = false, length = 15)
    private String status = "INTERESTED";

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}
