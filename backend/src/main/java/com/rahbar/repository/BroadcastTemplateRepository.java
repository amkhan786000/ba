package com.rahbar.repository;

import com.rahbar.entity.BroadcastTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BroadcastTemplateRepository extends JpaRepository<BroadcastTemplate, Long> {
    List<BroadcastTemplate> findAllByOrderByNameAsc();

    Optional<BroadcastTemplate> findByNameIgnoreCase(String name);
}
