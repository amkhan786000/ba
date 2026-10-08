package com.rahbar.repository;

import com.rahbar.entity.StudentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentStatusHistoryRepository extends JpaRepository<StudentStatusHistory, Long> {
    List<StudentStatusHistory> findByUserIdOrderByHistoryIdDesc(Long userId);
}
