package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An issue a student or sponsor raised with the office (Helpline). */
@Entity
@Table(name = "helpline_tickets")
@Getter @Setter
public class HelplineTicket extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "raised_by", nullable = false)
    private Long raisedBy;

    /** STUDENT or SPONSOR. */
    @Column(name = "raiser_kind", nullable = false, length = 10)
    private String raiserKind;

    @Column(name = "category", nullable = false, length = 15)
    private String category;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** Seen only by the Super Admin and whoever is assigned. */
    @Column(name = "confidential", nullable = false)
    private boolean confidential = false;

    /** OPEN, IN_PROGRESS, RESOLVED or CLOSED. */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "OPEN";

    /** NORMAL or URGENT. */
    @Column(name = "priority", nullable = false, length = 10)
    private String priority = "NORMAL";

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "resolved_at")
    private java.time.LocalDateTime resolvedAt;

    @Column(name = "last_activity_at")
    private java.time.LocalDateTime lastActivityAt;
}
