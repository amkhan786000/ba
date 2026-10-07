package com.rahbar.repository;

import com.rahbar.entity.BroadcastAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BroadcastAttachmentRepository extends JpaRepository<BroadcastAttachment, Long> {
    List<BroadcastAttachment> findByBroadcastIdIn(Collection<Long> broadcastIds);

    List<BroadcastAttachment> findByBroadcastIdOrderByAttachmentIdAsc(Long broadcastId);
}
