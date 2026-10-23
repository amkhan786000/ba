package com.rahbar.repository;

import com.rahbar.entity.CoachingExamPrize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingExamPrizeRepository extends JpaRepository<CoachingExamPrize, Long> {
    List<CoachingExamPrize> findByExamIdOrderByPositionAscTotalDesc(Long examId);

    List<CoachingExamPrize> findByExamIdIn(Collection<Long> examIds);

    void deleteByExamId(Long examId);
}
