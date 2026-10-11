package com.rahbar.repository;

import com.rahbar.entity.FinEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinEntryRepository extends JpaRepository<FinEntry, Long> {

    List<FinEntry> findByEntryDateBetweenOrderByEntryDateDescIdDesc(java.time.LocalDate from, java.time.LocalDate to);

    boolean existsByCategoryId(Long categoryId);
}
