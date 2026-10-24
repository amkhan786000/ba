package com.rahbar.repository;

import com.rahbar.entity.CareerInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareerInterestRepository extends JpaRepository<CareerInterest, Long> {
    List<CareerInterest> findByOpportunityId(Long opportunityId);

    List<CareerInterest> findByUserId(Long userId);

    Optional<CareerInterest> findByOpportunityIdAndUserId(Long opportunityId, Long userId);
}
