package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A role a person has besides their main one (users.role_id), e.g. a Super Admin who is also a Sponsor. */
@Entity
@Table(name = "user_additional_roles")
@Getter @Setter
public class UserAdditionalRole extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer roleId;
}
