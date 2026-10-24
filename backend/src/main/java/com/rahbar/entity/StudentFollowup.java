package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A follow-up call / visit with a student on the early-warning list (a scholarship or a coaching student). */
@Entity
@Table(name = "student_followups")
@Getter @Setter
public class StudentFollowup extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** SCHOLAR (users.id) or COACHING (coaching_students.id). */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "followup_date", nullable = false)
    private LocalDate followupDate;

    /** CALL, VISIT, MEETING or OTHER. */
    @Column(name = "method", nullable = false, length = 10)
    private String method;

    /** RESOLVED, MONITOR (keep watching) or ESCALATE. */
    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "note", length = 1000)
    private String note;

    /** When to follow up next (optional). */
    @Column(name = "next_date")
    private LocalDate nextDate;
}
