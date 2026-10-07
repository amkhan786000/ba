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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Human-facing user code (e.g. STU-1001, REG-..., 1005): unique, but not the key other tables link to. */
    @Column(name = "user_id", length = 50, nullable = false, unique = true)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "sex", nullable = false)
    private String sex; // 'M' or 'F'

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "phone",  unique = true)
    private String phone;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "status")
    private String status = "Active";

    /** The user's chapter (chapters.chapter_id); optional. */
    @Column(name = "chapter_id")
    private Long chapterId;

    /** The user's RCC center (rcc_centers.rcc_center_id); used to scope RCC coordinators. Optional. */
    @Column(name = "rcc_center_id")
    private Long rccCenterId;

    /** Name of the chapter, read with the user (not stored on users). */
    @org.hibernate.annotations.Formula("(select c.chapter_name from chapters c where c.chapter_id = chapter_id)")
    private String chapterName;

    @Column(name = "year")
    private Integer year;

    /** Set when an admin or a bulk upload creates the account: the user must choose a new password at first sign-in. */
    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;

}
