package com.rahbar.repository;

import com.rahbar.entity.RebQuranProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RebQuranProgressRepository extends JpaRepository<RebQuranProgress, Long> {

    List<RebQuranProgress> findByStudentIdOrderByDateDescIdDesc(Long studentId);

    List<RebQuranProgress> findByStudentIdIn(Collection<Long> studentIds);
}
