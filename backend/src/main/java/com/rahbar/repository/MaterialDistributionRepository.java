package com.rahbar.repository;

import com.rahbar.entity.MaterialDistribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialDistributionRepository extends JpaRepository<MaterialDistribution, Long> {
    List<MaterialDistribution> findByRecipientKindAndRecipientIdOrderByGivenOnDesc(String recipientKind, Long recipientId);

    List<MaterialDistribution> findAllByOrderByGivenOnDescIdDesc();
}
