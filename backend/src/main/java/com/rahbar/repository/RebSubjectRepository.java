package com.rahbar.repository;

import com.rahbar.entity.RebSubject;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebSubjectRepository extends JpaRepository<RebSubject, Long> {

    java.util.List<RebSubject> findAllByOrderByTrackAscNameAsc();
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByName(String name);
}
