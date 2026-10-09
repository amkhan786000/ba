package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A browser / device a user allowed notifications on (Web Push subscription). */
@Entity
@Table(name = "push_subscriptions")
@Getter @Setter
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The push service URL the browser gave us. */
    @Column(name = "endpoint", nullable = false, length = 1000)
    private String endpoint;

    /** SHA-256 of the endpoint (unique; endpoints are too long to index). */
    @Column(name = "endpoint_hash", nullable = false, length = 64, unique = true)
    private String endpointHash;

    /** The browser's public key (base64url) used to encrypt messages. */
    @Column(name = "p256dh", nullable = false, length = 200)
    private String p256dh;

    /** The browser's auth secret (base64url). */
    @Column(name = "auth", nullable = false, length = 100)
    private String auth;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "failures", nullable = false)
    private Integer failures = 0;
}
