package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "notifications",
       uniqueConstraints = @UniqueConstraint(name = "uq_notifications_ref_key", columnNames = "ref_key"))
@Getter @Setter
public class Notification extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "status")
    private String status = "Unread";

    @Column(name = "notification_date")
    private LocalDate notificationDate;

    @Column(name = "title")
    private String title;

    /** Drives the icon in the bell menu: application, payment, reminder, progress, mapping, account. */
    @Column(name = "category", length = 50)
    private String category;

    /** In-app route to open when the notification is clicked, e.g. /sponsor/payments. */
    @Column(name = "link")
    private String link;

    /** De-duplication key: a notification with the same key is never created twice (payment reminders). */
    @Column(name = "ref_key", length = 191)
    private String refKey;

}
