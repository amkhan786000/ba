package com.rahbar.repository;

import com.rahbar.entity.RebInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebInvoiceLineRepository extends JpaRepository<RebInvoiceLine, Long> {

    List<RebInvoiceLine> findByInvoiceId(Long invoiceId);

    List<RebInvoiceLine> findByInvoiceIdIn(Collection<Long> invoiceIds);
}
