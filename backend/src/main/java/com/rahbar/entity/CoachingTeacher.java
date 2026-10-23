package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A coaching center teacher. May be linked to a Rahbar login (role Coaching Teacher) to see their center's records. */
@Entity
@Table(name = "coaching_teachers")
@Getter @Setter
public class CoachingTeacher extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rcc_center_id", nullable = false)
    private Long rccCenterId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "gender", length = 1)
    private String gender;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "qualification", length = 150)
    private String qualification;

    /** Subject names, comma separated. */
    @Column(name = "subjects", length = 300)
    private String subjects;

    /** Classes taught, e.g. "7,8". */
    @Column(name = "classes", length = 20)
    private String classes;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "remarks", length = 500)
    private String remarks;

    /** The teacher's Rahbar login (users.id), if they have one. */
    @Column(name = "user_id")
    private Long userId;
}
