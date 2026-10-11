package com.rahbar.repository;

import com.rahbar.entity.RebFeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebFeeStructureRepository extends JpaRepository<RebFeeStructure, Long> {

    List<RebFeeStructure> findBySessionId(Long sessionId);

    List<RebFeeStructure> findBySessionIdAndClassId(Long sessionId, Long classId);

    boolean existsByFeeHeadId(Long feeHeadId);
}
