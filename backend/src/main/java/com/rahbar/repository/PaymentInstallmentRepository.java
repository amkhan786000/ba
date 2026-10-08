package com.rahbar.repository;

import com.rahbar.entity.PaymentInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentInstallmentRepository extends JpaRepository<PaymentInstallment, Long> {
    List<PaymentInstallment> findByGranteeIdOrderByInstallmentNoAsc(Long granteeId);

    List<PaymentInstallment> findByGranteeIdInOrderByGranteeIdAscInstallmentNoAsc(Collection<Long> granteeIds);

    List<PaymentInstallment> findByGrantorIdOrderByGranteeIdAscInstallmentNoAsc(Long grantorId);

    List<PaymentInstallment> findByGrantorIdAndGranteeIdOrderByInstallmentNoAsc(Long grantorId, Long granteeId);

    Optional<PaymentInstallment> findByPaymentId(Long paymentId);

    List<PaymentInstallment> findAllByOrderByGranteeIdAscInstallmentNoAsc();

    @Query("select distinct i.granteeId from PaymentInstallment i")
    List<Long> findGranteeIds();
}
