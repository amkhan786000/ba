package com.rahbar.repository;

import com.rahbar.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    /** Newest first; every filter is optional (q matches address or subject, as a LIKE pattern). */
    @Query("""
        select e from EmailLog e
        where (:q is null or lower(e.toAddress) like :q or lower(e.subject) like :q)
          and (:status is null or e.status = :status)
          and (:recipientId is null or e.recipientUserId = :recipientId)
          and (:from is null or e.sentAt >= :from)
          and (:to is null or e.sentAt < :to)
        """)
    Page<EmailLog> search(@Param("q") String q, @Param("status") String status, @Param("recipientId") Long recipientId,
                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
