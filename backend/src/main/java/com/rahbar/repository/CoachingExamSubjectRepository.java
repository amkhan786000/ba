package com.rahbar.repository;

import com.rahbar.entity.CoachingExamSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingExamSubjectRepository extends JpaRepository<CoachingExamSubject, Long> {
    List<CoachingExamSubject> findByExamIdOrderByIdAsc(Long examId);

    List<CoachingExamSubject> findByExamIdIn(Collection<Long> examIds);

    boolean existsBySubjectId(Long subjectId);

    void deleteByExamId(Long examId);
}
