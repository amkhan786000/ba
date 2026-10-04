package com.rahbar.repository;

import com.rahbar.entity.ApplicationPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApplicationPeriodRepository extends JpaRepository<ApplicationPeriod, Long> {
    Optional<ApplicationPeriod> findFirstByIsActiveTrueOrderByStartDateDesc();
    List<ApplicationPeriod> findByIsActiveTrue();
    /** Is there an active period with start <= day <= end? */
    boolean existsByIsActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDateTime day1, LocalDateTime day2);
}
