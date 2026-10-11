package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A call, note or email to a sponsor who stopped paying or has no student now (Sponsor Follow-up). */
@Entity
@Table(name = "sponsor_followups")
@Getter @Setter
public class SponsorFollowup extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sponsorUserId;

    /** LAPSED (installments overdue) or AVAILABLE (no current student). */
    @Column(nullable = false, length = 10)
    private String kind;

    /** EMAIL, CALL or NOTE. */
    @Column(nullable = false, length = 10)
    private String channel;

    @Column(length = 1000)
    private String note;
}
