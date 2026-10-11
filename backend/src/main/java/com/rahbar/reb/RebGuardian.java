package com.rahbar.reb;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A parent or guardian of a girl. may_pick_up: allowed to take her home from the hostel. */
@Entity
@Table(name = "reb_guardians")
@Getter @Setter
public class RebGuardian extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long studentId;
    /** FATHER, MOTHER, BROTHER, SISTER, UNCLE, AUNT, GRANDPARENT or GUARDIAN. */
    @Column(nullable = false, length = 20)
    private String relation = "FATHER";
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 20)
    private String phone;
    @Column(length = 20)
    private String whatsapp;
    @Column(length = 150)
    private String email;
    @Column(length = 120)
    private String occupation;
    private java.math.BigDecimal monthlyIncome;
    @Column(name = "is_primary")
    private boolean primary;
    private boolean mayPickUp = true;
}
