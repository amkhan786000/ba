package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A message an admin emailed to one person or a group (Admin > Broadcast Messages), with its delivery counts. */
@Entity
@Table(name = "broadcast_messages")
@Getter @Setter
public class BroadcastMessage extends Modifiable {
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "broadcast_id")
    private Long broadcastId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Who it went to, in words, e.g. "Roles: Convenor, Sponsor" or "Ayesha Khan (STU-1001)". */
    @Column(name = "audience", nullable = false, length = 500)
    private String audience;

    @Column(name = "recipients", nullable = false)
    private Integer recipients = 0;

    @Column(name = "sent", nullable = false)
    private Integer sent = 0;

    /** Recipients without a usable email address. */
    @Column(name = "skipped", nullable = false)
    private Integer skipped = 0;

    @Column(name = "failed", nullable = false)
    private Integer failed = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = SENDING;
}
