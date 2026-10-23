package com.rahbar.repository;

import com.rahbar.entity.CoachingSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CoachingSubjectRepository extends JpaRepository<CoachingSubject, Long> {
    List<CoachingSubject> findAllByOrderBySortOrderAscNameAsc();

    Optional<CoachingSubject> findByNameIgnoreCase(String name);
}
