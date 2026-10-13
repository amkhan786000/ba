package com.rahbar.repository;

import com.rahbar.entity.FinCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinCategoryRepository extends JpaRepository<FinCategory, Long> {
    List<FinCategory> findByBookOrderByKindAscNameAsc(String book);
    Optional<FinCategory> findByIdAndBook(Long id, String book);
    boolean existsByBookAndKindAndNameIgnoreCase(String book, String kind, String name);
    boolean existsByBookAndKindAndNameIgnoreCaseAndIdNot(String book, String kind, String name, Long id);
}
