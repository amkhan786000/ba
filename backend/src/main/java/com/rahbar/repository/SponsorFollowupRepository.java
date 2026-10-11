package com.rahbar.repository;

import com.rahbar.entity.SponsorFollowup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SponsorFollowupRepository extends JpaRepository<SponsorFollowup, Long> {

    List<SponsorFollowup> findAllByOrderByIdDesc();

    List<SponsorFollowup> findBySponsorUserIdOrderByIdDesc(Long sponsorUserId);
}
