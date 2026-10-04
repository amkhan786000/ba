package com.rahbar.repository;

import com.rahbar.entity.BankDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BankDetailsRepository extends JpaRepository<BankDetails, Long> {
    Optional<BankDetails> findByUserId(String userId);
    List<BankDetails> findByUserIdIn(List<String> userIds);
}
