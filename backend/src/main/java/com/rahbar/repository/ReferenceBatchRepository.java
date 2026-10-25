package com.rahbar.repository;

import com.rahbar.entity.ReferenceBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceBatchRepository extends JpaRepository<ReferenceBatch, Long> {
    List<ReferenceBatch> findBySessionYearOrderByIdDesc(Integer sessionYear);
}
