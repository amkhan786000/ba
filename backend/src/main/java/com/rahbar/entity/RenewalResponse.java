package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One sponsor in a renewal campaign: when they were asked and what they answered. */
@Entity
@Table(name = "renewal_responses")
@Getter @Setter
public class RenewalResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "sponsor_id", nullable = false)
    private Long sponsorId;

    /** How many students they had when the campaign started. */
    @Column(name = "current_students", nullable = false)
    private Integer currentStudents = 0;

    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    /** CONTINUE (same students), CHANGE (a different number) or STOP; null: not answered yet. */
    @Column(name = "answer", length = 10)
    private String answer;

    /** CHANGE: how many students they will sponsor. */
    @Column(name = "students_wanted")
    private Integer studentsWanted;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    /** Who gave the answer: the sponsor, or the office after a call. */
    @Column(name = "answered_by")
    private Long answeredBy;
}
