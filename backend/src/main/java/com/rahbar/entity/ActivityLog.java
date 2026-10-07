package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One line of the activity log: who did what, when (written by ActivityLogInterceptor and AuthService). */
@Entity
@Table(name = "activity_log")
@Getter @Setter
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "path")
    private String path;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
