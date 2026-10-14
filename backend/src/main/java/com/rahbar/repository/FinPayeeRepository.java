package com.rahbar.repository;

import com.rahbar.entity.FinPayee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinPayeeRepository extends JpaRepository<FinPayee, Long> {
    List<FinPayee> findByBookOrderByNameAsc(String book);
    Optional<FinPayee> findByIdAndBook(Long id, String book);
    boolean existsByBookAndNameIgnoreCase(String book, String name);
    boolean existsByBookAndNameIgnoreCaseAndIdNot(String book, String name, Long id);
}
