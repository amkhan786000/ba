package com.rahbar.repository;

import com.rahbar.entity.RebTeacherAssignment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebTeacherAssignmentRepository extends JpaRepository<RebTeacherAssignment, Long> {

    java.util.List<RebTeacherAssignment> findBySessionId(Long sessionId);
    java.util.List<RebTeacherAssignment> findByStaffId(Long staffId);
    boolean existsBySubjectId(Long subjectId);
}
