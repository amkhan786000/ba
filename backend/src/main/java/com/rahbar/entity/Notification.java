package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "notifications")
@Getter @Setter
public class Notification extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "status")
    private String status = "Unread";

    @Column(name = "notification_date")
    private LocalDate notificationDate;

}
