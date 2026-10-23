package com.rahbar.repository;

import com.rahbar.entity.CoachingExamMark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingExamMarkRepository extends JpaRepository<CoachingExamMark, Long> {
    List<CoachingExamMark> findByExamId(Long examId);

    boolean existsByStudentId(Long studentId);

    void deleteByExamId(Long examId);
}
