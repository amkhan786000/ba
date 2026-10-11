package com.rahbar.repository;

import com.rahbar.entity.RebSession;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebSessionRepository extends JpaRepository<RebSession, Long> {

    java.util.List<RebSession> findAllByOrderByStartDateDesc();
    java.util.Optional<RebSession> findFirstByCurrentTrue();
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByName(String name);
}
