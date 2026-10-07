package com.rahbar.repository;

import com.rahbar.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop30ByUserIdOrderByNotificationIdDesc(Long userId);
    long countByUserIdAndStatus(Long userId, String status);
    Optional<Notification> findByNotificationIdAndUserId(Long notificationId, Long userId);
    boolean existsByRefKey(String refKey);

    @Transactional
    @Modifying
    @Query("""
        update Notification n set n.status = 'Read', n.updatedBy = :userId, n.updatedAt = :now
        where n.userId = :userId and n.status = 'Unread'
        """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
