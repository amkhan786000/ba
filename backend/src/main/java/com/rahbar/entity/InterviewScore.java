package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** The score (1-10) one review gives one criterion. */
@Entity
@Table(name = "interview_scores")
@Getter @Setter
public class InterviewScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Long scoreId;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "criterion_id", nullable = false)
    private Long criterionId;

    @Column(name = "score", nullable = false)
    private Integer score;
}
