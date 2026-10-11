package com.rahbar.repository;

import com.rahbar.entity.RebExamResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebExamResultRepository extends JpaRepository<RebExamResult, Long> {

    List<RebExamResult> findByExamId(Long examId);

    Optional<RebExamResult> findByExamIdAndStudentId(Long examId, Long studentId);

    List<RebExamResult> findByStudentId(Long studentId);
}
