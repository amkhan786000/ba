package com.rahbar.repository;

import com.rahbar.entity.InterviewCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewCriterionRepository extends JpaRepository<InterviewCriterion, Long> {
    List<InterviewCriterion> findAllByOrderBySortOrderAscNameAsc();

    Optional<InterviewCriterion> findByNameIgnoreCase(String name);
}
