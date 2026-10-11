package com.rahbar.reb;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebClassRepository extends JpaRepository<RebClass, Long> {

    java.util.List<RebClass> findAllByOrderByLevelOrderAscNameAsc();
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByName(String name);
}
