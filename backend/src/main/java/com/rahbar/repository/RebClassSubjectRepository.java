package com.rahbar.repository;

import com.rahbar.entity.RebClassSubject;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebClassSubjectRepository extends JpaRepository<RebClassSubject, Long> {

    java.util.List<RebClassSubject> findBySessionIdAndClassIdOrderByDisplayOrderAscIdAsc(Long sessionId, Long classId);
    java.util.List<RebClassSubject> findBySessionId(Long sessionId);
    boolean existsBySubjectId(Long subjectId);
    boolean existsByClassId(Long classId);
}
