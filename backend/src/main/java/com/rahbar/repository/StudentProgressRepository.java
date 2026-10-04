package com.rahbar.repository;

import com.rahbar.entity.StudentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {
    List<StudentProgress> findByGranteeIdOrderByCreatedAtDesc(String granteeId);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(p.progressId),0) + 1 from StudentProgress p")
    Long nextId();
}
