package com.rahbar.repository;

import com.rahbar.entity.OnlinePayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OnlinePaymentRepository extends JpaRepository<OnlinePayment, Long> {
    /** Locked, so the browser's confirmation and Razorpay's webhook can't both record the same payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OnlinePayment p where p.gatewayOrderId = :orderId")
    Optional<OnlinePayment> lockByGatewayOrderId(@Param("orderId") String orderId);

    List<OnlinePayment> findAllByOrderByIdDesc();

    List<OnlinePayment> findBySponsorUserIdAndStatusOrderByPaidAtDesc(Long sponsorUserId, String status);
}
