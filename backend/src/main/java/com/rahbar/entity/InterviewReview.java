package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One interviewer's review of an application: a recommendation and a comment (scores in InterviewScore). */
@Entity
@Table(name = "interview_reviews")
@Getter @Setter
public class InterviewReview extends Modifiable {
    public static final String APPROVE = "APPROVE";
    public static final String WAITLIST = "WAITLIST";
    public static final String REJECT = "REJECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "grantee_detail_id", nullable = false)
    private Long granteeDetailId;

    /** users.id of the interviewer. */
    @Column(name = "interviewer_id", nullable = false)
    private Long interviewerId;

    /** APPROVE, WAITLIST or REJECT (optional). */
    @Column(name = "recommendation", length = 20)
    private String recommendation;

    @Column(name = "comment", length = 2000)
    private String comment;
}
