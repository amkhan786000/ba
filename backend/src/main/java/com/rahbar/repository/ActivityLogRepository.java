package com.rahbar.repository;

import com.rahbar.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /** Newest first; every filter is optional (null = no filter). search is a lower-case LIKE pattern. */
    @Query(value = """
        select a from ActivityLog a
        where (:userId is null or a.userId = :userId)
          and (:from is null or a.createdAt >= :from)
          and (:to is null or a.createdAt < :to)
          and (:search is null or lower(a.action) like :search or lower(a.userName) like :search
               or lower(a.userId) like :search or lower(a.path) like :search)
        order by a.createdAt desc, a.logId desc
        """,
        countQuery = """
        select count(a) from ActivityLog a
        where (:userId is null or a.userId = :userId)
          and (:from is null or a.createdAt >= :from)
          and (:to is null or a.createdAt < :to)
          and (:search is null or lower(a.action) like :search or lower(a.userName) like :search
               or lower(a.userId) like :search or lower(a.path) like :search)
        """)
    Page<ActivityLog> search(@Param("userId") String userId, @Param("search") String search,
                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    List<ActivityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
}
