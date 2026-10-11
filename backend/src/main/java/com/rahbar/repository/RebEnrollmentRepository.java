package com.rahbar.repository;

import com.rahbar.entity.RebEnrollment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebEnrollmentRepository extends JpaRepository<RebEnrollment, Long> {

    java.util.List<RebEnrollment> findBySessionId(Long sessionId);
    java.util.List<RebEnrollment> findByStudentId(Long studentId);
    java.util.Optional<RebEnrollment> findByStudentIdAndSessionId(Long studentId, Long sessionId);
    java.util.List<RebEnrollment> findBySectionId(Long sectionId);
    boolean existsBySectionId(Long sectionId);
}
