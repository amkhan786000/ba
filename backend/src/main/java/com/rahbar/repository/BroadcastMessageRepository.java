package com.rahbar.repository;

import com.rahbar.entity.BroadcastMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, Long> {
    List<BroadcastMessage> findTop100ByOrderByBroadcastIdDesc();
}
