package com.rahbar.repository;

import com.rahbar.entity.ReferenceCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferenceCounterRepository extends JpaRepository<ReferenceCounter, Long> {
    Optional<ReferenceCounter> findBySessionYearAndPrefix(Integer sessionYear, String prefix);

    List<ReferenceCounter> findBySessionYear(Integer sessionYear);
}
