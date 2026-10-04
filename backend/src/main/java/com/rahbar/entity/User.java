package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends Modifiable {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "sex", nullable = false)
    private String sex; // 'M' or 'F'

    @com.fasterxml.jackson.annotation.JsonIgnore
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

}
