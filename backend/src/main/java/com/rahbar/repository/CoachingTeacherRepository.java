package com.rahbar.repository;

import com.rahbar.entity.CoachingTeacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingTeacherRepository extends JpaRepository<CoachingTeacher, Long> {
    Optional<CoachingTeacher> findByUserId(Long userId);
}
