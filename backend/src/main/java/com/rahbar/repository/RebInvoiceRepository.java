package com.rahbar.repository;

import com.rahbar.entity.RebInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebInvoiceRepository extends JpaRepository<RebInvoice, Long> {

    List<RebInvoice> findByStudentIdOrderByPeriodAsc(Long studentId);

    List<RebInvoice> findByStudentIdInOrderByPeriodAsc(Collection<Long> studentIds);

    List<RebInvoice> findByPeriod(String period);

    List<RebInvoice> findByStatusIn(Collection<String> statuses);

    List<RebInvoice> findBySessionId(Long sessionId);

    @Query("select i.invoiceNo from RebInvoice i where i.invoiceNo like :prefix%")
    List<String> findNumbersStartingWith(@Param("prefix") String prefix);
}
