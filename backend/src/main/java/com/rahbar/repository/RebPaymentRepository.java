package com.rahbar.repository;

import com.rahbar.entity.RebPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebPaymentRepository extends JpaRepository<RebPayment, Long> {

    List<RebPayment> findByStudentIdOrderByPaidOnAscIdAsc(Long studentId);

    List<RebPayment> findByPaidOnBetweenOrderByPaidOnAscIdAsc(java.time.LocalDate from, java.time.LocalDate to);

    @Query("select p.receiptNo from RebPayment p where p.receiptNo like :prefix%")
    List<String> findNumbersStartingWith(@Param("prefix") String prefix);
}
