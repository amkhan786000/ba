package com.rahbar.repository;

import com.rahbar.entity.FinPublishedYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinPublishedYearRepository extends JpaRepository<FinPublishedYear, Long> {
    List<FinPublishedYear> findByBookOrderByYearDesc(String book);
    Optional<FinPublishedYear> findByBookAndYear(String book, Integer year);
    boolean existsByBookAndYear(String book, Integer year);
}
