package com.rahbar.repository;

import com.rahbar.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {
    Optional<PasswordResetCode> findFirstByUserIdAndUsedFalseOrderByResetIdDesc(Long userId);

    List<PasswordResetCode> findByUserIdAndUsedFalse(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
}
