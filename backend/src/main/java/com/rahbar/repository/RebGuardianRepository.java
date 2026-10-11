package com.rahbar.repository;

import com.rahbar.entity.RebGuardian;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebGuardianRepository extends JpaRepository<RebGuardian, Long> {

    java.util.List<RebGuardian> findByStudentIdOrderByPrimaryDescIdAsc(Long studentId);
    java.util.List<RebGuardian> findByStudentIdIn(java.util.Collection<Long> studentIds);
}
