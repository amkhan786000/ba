package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "sex", nullable = false)
    private String sex; // 'M' or 'F'

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone", nullable = false, unique = true)
    private String phone;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "status")
    private String status = "Active";

    @Column(name = "region")
    private String region = "Jeddah";

    @Column(name = "year")
    private Integer year;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
