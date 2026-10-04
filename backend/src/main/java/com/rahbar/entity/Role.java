package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends Modifiable {
    @Id
    // Assigned as MAX + 1 by RoleService (built-in roles keep their fixed ids 1-8)
    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "description")
    private String description;
}
