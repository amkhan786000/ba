package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A teacher or other staff member of REB, optionally with a Rahbar login (user_id). */
@Entity
@Table(name = "reb_staff")
@Getter @Setter
public class RebStaff extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Column(nullable = false, length = 120)
    private String name;
    /** TEACHER, PRINCIPAL, WARDEN, OFFICE or OTHER. */
    @Column(nullable = false, length = 20)
    private String designation = "TEACHER";
    @Column(length = 20)
    private String phone;
    @Column(length = 150)
    private String email;
    @Column(length = 150)
    private String qualification;
    private java.time.LocalDate joinedOn;
    private java.time.LocalDate leftOn;
    /** ACTIVE or LEFT. */
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";
    @Column(length = 1000)
    private String notes;
}
