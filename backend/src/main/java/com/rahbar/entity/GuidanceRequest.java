package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A student asking for career guidance, and the session the office arranged. */
@Entity
@Table(name = "guidance_requests")
@Getter @Setter
public class GuidanceRequest extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** COURSE, JOBS, HIGHER_STUDIES, EXAMS or OTHER. */
    @Column(name = "topic", nullable = false, length = 20)
    private String topic;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    /** OPEN, SCHEDULED, DONE or CLOSED. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "session_at")
    private java.time.LocalDateTime sessionAt;

    @Column(name = "outcome", length = 2000)
    private String outcome;
}
