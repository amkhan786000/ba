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

    /** Comma-separated permission keys such as "USERS:VIEW,USERS:EDIT" (see security.Section). */
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;

    /**
     * Which records the role's permissions cover: ALL, CHAPTER (only the user's own chapter) or
     * RCC (only the user's own RCC center).
     */
    @Column(name = "scope", nullable = false, length = 20)
    @org.hibernate.annotations.ColumnDefault("'ALL'")
    private String scope = SCOPE_ALL;

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_CHAPTER = "CHAPTER";
    public static final String SCOPE_RCC = "RCC";
    public static final int SUPER_ADMIN = 1;

    public java.util.Set<String> permissionSet() {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        if (permissions != null) {
            for (String p : permissions.split(",")) if (!p.isBlank()) set.add(p.trim());
        }
        return set;
    }
}
