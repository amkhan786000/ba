package com.rahbar.repository;

import com.rahbar.entity.HelplineMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HelplineMessageRepository extends JpaRepository<HelplineMessage, Long> {
    List<HelplineMessage> findByTicketIdOrderByIdAsc(Long ticketId);
}
