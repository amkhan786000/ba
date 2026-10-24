package com.rahbar.repository;

import com.rahbar.entity.CoachingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CoachingAttendanceRepository extends JpaRepository<CoachingAttendance, Long> {
    List<CoachingAttendance> findByRccCenterIdAndClassLevelAndAttDate(Long rccCenterId, Integer classLevel, LocalDate attDate);

    List<CoachingAttendance> findByAttDateBetween(LocalDate from, LocalDate to);

    List<CoachingAttendance> findByStudentIdInAndAttDateBetween(Collection<Long> studentIds, LocalDate from, LocalDate to);
}
