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
}
