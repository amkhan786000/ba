package com.rahbar.repository;

import com.rahbar.entity.FinEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinEntryRepository extends JpaRepository<FinEntry, Long> {

    List<FinEntry> findByBookAndEntryDateBetweenOrderByEntryDateDescIdDesc(String book, java.time.LocalDate from, java.time.LocalDate to);
    java.util.Optional<FinEntry> findByIdAndBook(Long id, String book);

    boolean existsByCategoryId(Long categoryId);

    /** Total of one kind (INCOME / EXPENSE) up to and including a day. */
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.amount), 0) from FinEntry e where e.book = :book and e.kind = :kind and e.entryDate <= :upTo")
    java.math.BigDecimal totalUpTo(@org.springframework.data.repository.query.Param("book") String book, @org.springframework.data.repository.query.Param("kind") String kind,
                                   @org.springframework.data.repository.query.Param("upTo") java.time.LocalDate upTo);

    /** Total of one kind between two days (inclusive). */
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.amount), 0) from FinEntry e where e.book = :book and e.kind = :kind and e.entryDate between :from and :to")
    java.math.BigDecimal totalBetween(@org.springframework.data.repository.query.Param("book") String book, @org.springframework.data.repository.query.Param("kind") String kind,
                                      @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
                                      @org.springframework.data.repository.query.Param("to") java.time.LocalDate to);

    java.util.Optional<FinEntry> findFirstByBookOrderByEntryDateDescIdDesc(String book);
}
