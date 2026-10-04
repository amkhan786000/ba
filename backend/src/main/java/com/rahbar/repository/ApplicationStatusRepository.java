package com.rahbar.repository;

import com.rahbar.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatus, Long> {
    List<ApplicationStatus> findByGranteeDetailIdOrderByCreatedAtDesc(Long granteeDetailId);
    Optional<ApplicationStatus> findFirstByGranteeDetailIdOrderByCreatedAtDesc(Long granteeDetailId);

    /** The most recent status row of every application (by created_at). */
    @Query("""
        select s from ApplicationStatus s
        where s.createdAt = (select max(s2.createdAt) from ApplicationStatus s2 where s2.granteeDetailId = s.granteeDetailId)
        """)
    List<ApplicationStatus> findLatestPerApplication();
}
