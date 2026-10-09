package com.rahbar.repository;

import com.rahbar.entity.InterviewScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InterviewScoreRepository extends JpaRepository<InterviewScore, Long> {
    List<InterviewScore> findByReviewIdIn(Collection<Long> reviewIds);

    List<InterviewScore> findByReviewId(Long reviewId);

    long countByCriterionId(Long criterionId);
}
