package com.rahbar.repository;

import com.rahbar.entity.SponsorshipApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SponsorshipApplicationRepository extends JpaRepository<SponsorshipApplication, Long> {
    Optional<SponsorshipApplication> findByPeriodIdAndSponsorId(Long periodId, Long sponsorId);

    List<SponsorshipApplication> findByPeriodIdOrderByCreatedAtDesc(Long periodId);
}
