package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Every email the application sent or tried to send (Admin > Email Log). Codes and passwords are hidden. */
@Entity
@Table(name = "email_log")
@Getter @Setter
public class EmailLog {
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_id")
    private Long emailId;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    /** users.id of the recipient when the address belongs to a user. */
    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "subject", length = 500)
    private String subject;

    @Lob
    @Column(name = "body", columnDefinition = "MEDIUMTEXT")
    private String body;

    /** Attachment file names, comma-separated. */
    @Column(name = "attachments", length = 2000)
    private String attachments;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "error", length = 1000)
    private String error;

    /** users.id of the signed-in user whose action sent it; null for scheduled jobs and public pages. */
    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
