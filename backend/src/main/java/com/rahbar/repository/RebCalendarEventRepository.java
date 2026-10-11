package com.rahbar.repository;

import com.rahbar.entity.RebCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebCalendarEventRepository extends JpaRepository<RebCalendarEvent, Long> {

    List<RebCalendarEvent> findBySessionIdOrderByDateFromAsc(Long sessionId);
}
