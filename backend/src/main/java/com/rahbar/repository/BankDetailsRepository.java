package com.rahbar.repository;

import com.rahbar.entity.BankDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BankDetailsRepository extends JpaRepository<BankDetails, Long> {
    Optional<BankDetails> findFirstByUserId(Long userId);
    List<BankDetails> findByUserIdIn(List<Long> userIds);

    /** bank_detail_id is assigned as MAX + 1 (the legacy table isn't auto-increment everywhere). */
    @Query("select coalesce(max(b.bankDetailId), 0) + 1 from BankDetails b")
    Long nextId();
}
