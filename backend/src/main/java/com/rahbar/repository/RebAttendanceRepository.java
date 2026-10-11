package com.rahbar.repository;

import com.rahbar.entity.RebAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebAttendanceRepository extends JpaRepository<RebAttendance, Long> {

    List<RebAttendance> findBySectionIdAndDate(Long sectionId, LocalDate date);

    List<RebAttendance> findBySectionIdAndDateBetween(Long sectionId, LocalDate from, LocalDate to);

    List<RebAttendance> findByStudentIdAndDateBetween(Long studentId, LocalDate from, LocalDate to);

    List<RebAttendance> findByStudentIdInAndDateBetween(Collection<Long> studentIds, LocalDate from, LocalDate to);

    Optional<RebAttendance> findByStudentIdAndDate(Long studentId, LocalDate date);
}
