package com.rahbar.repository;

import com.rahbar.entity.ProgressDueDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProgressDueDateRepository extends JpaRepository<ProgressDueDate, Long> {
    List<ProgressDueDate> findAllByOrderByDueDateAsc();

    Optional<ProgressDueDate> findByDueDate(LocalDate dueDate);
}
