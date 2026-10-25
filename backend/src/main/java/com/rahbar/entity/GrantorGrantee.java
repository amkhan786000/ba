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

    @Column(name = "grantor_id", nullable = false)
    private Long grantorId;

    @Column(name = "grantee_id", nullable = false)
    private Long granteeId;

    @Column(name = "status")
    private String status = "Pending";

    /** On behalf of an anonymous sponsor (see dto.OnBehalf): the sponsor is the contact and the sponsor of record. */
    @Column(name = "on_behalf", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private Boolean onBehalf = false;

    /** The anonymous donor's chapter (chapters.chapter_id). */
    @Column(name = "donor_chapter_id")
    private Long donorChapterId;

    /** A short label for the donor, e.g. "Donor A" (never a name). */
    @Column(name = "donor_label", length = 80)
    private String donorLabel;

    /** The anonymous sponsor's own account (users.id), when linked; only the Super Admin and App Admin see who it is. */
    @Column(name = "donor_user_id")
    private Long donorUserId;

}
