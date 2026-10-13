package com.rahbar.repository;

import com.rahbar.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    Optional<TrustedDevice> findByTokenHash(String tokenHash);
    List<TrustedDevice> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<TrustedDevice> findByIdAndUserId(Long id, Long userId);

    @Transactional
    long deleteByUserId(Long userId);

    @Transactional
    long deleteByExpiresAtBefore(LocalDateTime time);
}
