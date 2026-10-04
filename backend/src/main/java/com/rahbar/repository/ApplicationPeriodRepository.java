package com.rahbar.repository;

import com.rahbar.entity.ApplicationPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationPeriodRepository extends JpaRepository<ApplicationPeriod, Long> {
    Optional<ApplicationPeriod> findFirstByIsActiveTrueOrderByStartDateDesc();
    List<ApplicationPeriod> findByIsActiveTrue();
}
