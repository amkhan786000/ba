package com.rahbar.repository;

import com.rahbar.entity.GuidanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuidanceRequestRepository extends JpaRepository<GuidanceRequest, Long> {
    List<GuidanceRequest> findByUserIdOrderByIdDesc(Long userId);

    List<GuidanceRequest> findAllByOrderByIdDesc();
}
