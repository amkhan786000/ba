package com.rahbar.repository;

import com.rahbar.entity.StudentFollowup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentFollowupRepository extends JpaRepository<StudentFollowup, Long> {
    List<StudentFollowup> findByKindAndStudentIdOrderByFollowupDateDescIdDesc(String kind, Long studentId);

    List<StudentFollowup> findByKindOrderByFollowupDateDescIdDesc(String kind);
}
