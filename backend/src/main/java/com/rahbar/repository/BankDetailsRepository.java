package com.rahbar.repository;

import com.rahbar.entity.BankDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BankDetailsRepository extends JpaRepository<BankDetails, Long> {
    Optional<BankDetails> findFirstByUserId(String userId);
    List<BankDetails> findByUserIdIn(List<String> userIds);

    /** bank_detail_id is assigned as MAX + 1 (the legacy table isn't auto-increment everywhere). */
    @Query("select coalesce(max(b.bankDetailId), 0) + 1 from BankDetails b")
    Long nextId();

    @Transactional
    @Modifying
    @Query("delete from BankDetails b where b.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}
