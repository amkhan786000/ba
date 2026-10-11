package com.rahbar.repository;

import com.rahbar.entity.RebGradeBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebGradeBandRepository extends JpaRepository<RebGradeBand, Long> {

    List<RebGradeBand> findBySessionIdOrderByMinPercentDesc(Long sessionId);
}
