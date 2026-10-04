package com.rahbar.repository;

import com.rahbar.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByGranteeIdOrderByPaymentDateAsc(String granteeId);
    List<Payment> findByGranteeIdAndStatusOrderByPaymentDateDesc(String granteeId, String status);
    List<Payment> findByGranteeIdInAndStatusInOrderByPaymentDateAsc(List<String> granteeIds, List<String> statuses);
    List<Payment> findByGrantorIdOrderByPaymentDateDesc(String grantorId);
    long countByGranteeId(String granteeId);
}
