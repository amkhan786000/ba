package com.rahbar.repository;

import com.rahbar.entity.RebExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebExamRepository extends JpaRepository<RebExam, Long> {

    List<RebExam> findBySessionIdOrderByStartDateAscIdAsc(Long sessionId);
}
