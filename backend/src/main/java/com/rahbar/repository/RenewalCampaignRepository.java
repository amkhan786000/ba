package com.rahbar.repository;

import com.rahbar.entity.RenewalCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenewalCampaignRepository extends JpaRepository<RenewalCampaign, Long> {
    List<RenewalCampaign> findAllByOrderByIdDesc();

    Optional<RenewalCampaign> findFirstByClosedAtIsNullOrderByIdDesc();
}
