package com.rahbar.repository;

import com.rahbar.entity.SponsorMappingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SponsorMappingHistoryRepository extends JpaRepository<SponsorMappingHistory, Long> {
    List<SponsorMappingHistory> findByGrantorIdOrderByStartedAtDesc(Long grantorId);

    List<SponsorMappingHistory> findByGranteeIdAndEndedAtIsNull(Long granteeId);
}
