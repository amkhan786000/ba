package com.rahbar.repository;

import com.rahbar.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatus, Long> {
    List<ApplicationStatus> findByGranteeDetailIdOrderByCreatedAtDesc(Long granteeDetailId);
    Optional<ApplicationStatus> findFirstByGranteeDetailIdOrderByCreatedAtDesc(Long granteeDetailId);
}
