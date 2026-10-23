package com.rahbar.repository;

import com.rahbar.entity.CoachingExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingExamRepository extends JpaRepository<CoachingExam, Long> {
    List<CoachingExam> findAllByOrderByExamDateDescIdDesc();
}
