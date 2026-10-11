package com.rahbar.repository;

import com.rahbar.entity.FinCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinCategoryRepository extends JpaRepository<FinCategory, Long> {

    List<FinCategory> findAllByOrderByKindAscNameAsc();

    boolean existsByKindAndNameIgnoreCase(String kind, String name);

    boolean existsByKindAndNameIgnoreCaseAndIdNot(String kind, String name, Long id);
}
