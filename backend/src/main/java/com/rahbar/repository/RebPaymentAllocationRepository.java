package com.rahbar.repository;

import com.rahbar.entity.RebPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebPaymentAllocationRepository extends JpaRepository<RebPaymentAllocation, Long> {

    List<RebPaymentAllocation> findByPaymentId(Long paymentId);

    List<RebPaymentAllocation> findByInvoiceId(Long invoiceId);

    List<RebPaymentAllocation> findByPaymentIdIn(Collection<Long> paymentIds);
}
