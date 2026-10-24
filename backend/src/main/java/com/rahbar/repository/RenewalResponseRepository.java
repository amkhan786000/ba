package com.rahbar.repository;

import com.rahbar.entity.RenewalResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenewalResponseRepository extends JpaRepository<RenewalResponse, Long> {
    List<RenewalResponse> findByCampaignId(Long campaignId);

    Optional<RenewalResponse> findByCampaignIdAndSponsorId(Long campaignId, Long sponsorId);
}
