package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A period a student was mapped to a sponsor (endedAt null = still mapped). */
@Entity
@Table(name = "sponsor_mapping_history")
@Getter @Setter
public class SponsorMappingHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "grantee_id", nullable = false)
    private Long granteeId;

    @Column(name = "grantor_id", nullable = false)
    private Long grantorId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** Why it ended (e.g. "Sponsor declined to continue"), when the office gave one. */
    @Column(name = "end_reason", length = 300)
    private String endReason;

    /** On behalf of an anonymous donor (see dto.OnBehalf): the sponsor is the contact, the donor has no account. */
    @Column(name = "on_behalf", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private Boolean onBehalf = false;

    /** The anonymous donor's chapter (chapters.chapter_id). */
    @Column(name = "donor_chapter_id")
    private Long donorChapterId;

    /** A short label for the donor, e.g. "Donor A" (never a name). */
    @Column(name = "donor_label", length = 80)
    private String donorLabel;
}
