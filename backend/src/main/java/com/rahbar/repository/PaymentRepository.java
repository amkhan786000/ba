package com.rahbar.repository;

import com.rahbar.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByGranteeIdOrderByPaymentDateAsc(Long granteeId);
    List<Payment> findByGranteeIdOrderByPaymentDateDesc(Long granteeId);
    List<Payment> findByGranteeIdAndStatusOrderByPaymentDateDesc(Long granteeId, String status);
    List<Payment> findByGranteeIdAndStatusInOrderByPaymentDateAsc(Long granteeId, List<String> statuses);
    List<Payment> findByGranteeIdInAndStatusInOrderByPaymentDateAsc(List<Long> granteeIds, List<String> statuses);
    List<Payment> findByGrantorIdOrderByPaymentDateDesc(Long grantorId);
    List<Payment> findTop5ByGrantorIdAndStatusOrderByPaymentDateDesc(Long grantorId, String status);
    Optional<Payment> findFirstByGranteeIdOrderByCreatedAtDesc(Long granteeId);
    Optional<Payment> findByPaymentIdAndGranteeId(Long paymentId, Long granteeId);
    long countByGranteeIdAndStatus(Long granteeId, String status);
    List<Payment> findByStatusIgnoreCaseOrderByPaymentDateDesc(String status);
    boolean existsByGranteeIdAndPaymentDateGreaterThanEqual(Long granteeId, LocalDateTime since);

    @Query("select distinct extract(year from p.paymentDate) from Payment p where p.paymentDate is not null")
    List<Integer> findPaymentYears();

    @Query("select count(p) from Payment p where extract(year from p.paymentDate) = :year")
    long countPaidInYear(@Param("year") Integer year);

    /** Every payment with its student and (if any) sponsor account: [Payment, User grantee, User grantor]. */
    @Query("""
        select p, u1, u2 from Payment p
        join User u1 on u1.id = p.granteeId
        left join User u2 on u2.id = p.grantorId
        """)
    List<Object[]> findAllWithUsers();

    /** Payments report rows. */
    @Query("""
        select new map(p.paymentId as payment_id, gu.name as grantee_name, su.name as grantor_name,
                       su.userId as grantor_id, p.amount as amount, p.status as payment_status,
                       p.paymentDate as payment_date)
        from Payment p
        left join User gu on gu.id = p.granteeId
        left join User su on su.id = p.grantorId
        """)
    List<Map<String, Object>> findPaymentsReport();
}
