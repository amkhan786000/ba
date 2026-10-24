package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A message on a helpline issue; internal notes are for staff only. */
@Entity
@Table(name = "helpline_messages")
@Getter @Setter
public class HelplineMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "internal", nullable = false)
    private boolean internal = false;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;
}
