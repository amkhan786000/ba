package com.rahbar.repository;

import com.rahbar.entity.GranteeDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Students' family details (grantee_details). Until V45 scholarship applications were also kept here; new applications
 * are in scholarship_applications (ScholarshipApplicationRepository).
 */
public interface GranteeDetailsRepository extends JpaRepository<GranteeDetails, Long> {
    /** user_id isn't unique in the legacy table, so take the oldest row of a student. */
    Optional<GranteeDetails> findFirstByUserIdOrderByGranteeDetailIdAsc(Long userId);
    List<GranteeDetails> findByUserId(Long userId);
}
