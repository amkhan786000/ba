package com.rahbar.repository;

import com.rahbar.entity.BroadcastMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, Long> {
    org.springframework.data.domain.Page<BroadcastMessage> findAllByOrderByBroadcastIdDesc(org.springframework.data.domain.Pageable page);

    boolean existsByStatus(String status);

    /** Scheduled messages whose time has come. */
    List<BroadcastMessage> findByStatusAndScheduledAtLessThanEqual(String status, java.time.LocalDateTime time);
}
