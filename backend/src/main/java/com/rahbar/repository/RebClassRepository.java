package com.rahbar.repository;

import com.rahbar.entity.RebClass;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebClassRepository extends JpaRepository<RebClass, Long> {

    java.util.List<RebClass> findAllByOrderByLevelOrderAscNameAsc();
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByName(String name);
}
