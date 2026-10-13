package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A browser a user chose to remember after the emailed sign-in code: it skips the code until it expires. */
@Entity
@Table(name = "trusted_devices")
@Getter @Setter
public class TrustedDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 (hex) of the random token in the browser's cookie; the token itself is never stored. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
