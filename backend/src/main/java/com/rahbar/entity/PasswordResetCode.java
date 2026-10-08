package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A "Forgot password" code emailed to a user (only its hash is stored). */
@Entity
@Table(name = "password_reset_codes")
@Getter @Setter
public class PasswordResetCode extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reset_id")
    private Long resetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Wrong guesses so far; the code stops working after AuthService.MAX_ATTEMPTS. */
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "used", nullable = false)
    private Boolean used = false;
}
