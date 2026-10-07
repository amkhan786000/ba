package com.rahbar.repository;

import com.rahbar.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Otp.OtpId> {
    Optional<Otp> findByUserIdAndOtpAndStatus(Long userId, String otp, Integer status);
}
