package com.rahbar.repository;

import com.rahbar.entity.RebConcession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebConcessionRepository extends JpaRepository<RebConcession, Long> {

    List<RebConcession> findBySessionId(Long sessionId);

    List<RebConcession> findByStudentIdOrderByIdDesc(Long studentId);
}
