package com.rahbar.repository;

import com.rahbar.entity.CoachingStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingStudentRepository extends JpaRepository<CoachingStudent, Long> {
    List<CoachingStudent> findByRccCenterIdAndClassLevelAndStatusOrderByNameAsc(Long rccCenterId, Integer classLevel, String status);
}
