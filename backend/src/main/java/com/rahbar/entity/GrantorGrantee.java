package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grantor_grantees")
@Getter @Setter
public class GrantorGrantee extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grantor_grantee_id")
    private Long grantorGranteeId;

    @Column(name = "grantor_id", nullable = false, length = 50)
    private String grantorId;

    @Column(name = "grantee_id", nullable = false, length = 50)
    private String granteeId;

    @Column(name = "status")
    private String status = "Pending";

}
