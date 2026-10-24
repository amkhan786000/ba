package com.rahbar.repository;

import com.rahbar.entity.CareerOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareerOpportunityRepository extends JpaRepository<CareerOpportunity, Long> {
    List<CareerOpportunity> findAllByOrderByIdDesc();
}
