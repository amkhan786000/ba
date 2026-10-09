package com.rahbar.repository;

import com.rahbar.entity.InterviewReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewReviewRepository extends JpaRepository<InterviewReview, Long> {
    List<InterviewReview> findByGranteeDetailIdOrderByReviewIdAsc(Long granteeDetailId);

    List<InterviewReview> findByGranteeDetailIdIn(Collection<Long> granteeDetailIds);

    Optional<InterviewReview> findByGranteeDetailIdAndInterviewerId(Long granteeDetailId, Long interviewerId);
}
