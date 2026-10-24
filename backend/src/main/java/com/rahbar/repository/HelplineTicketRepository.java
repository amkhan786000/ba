package com.rahbar.repository;

import com.rahbar.entity.HelplineTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HelplineTicketRepository extends JpaRepository<HelplineTicket, Long> {
    List<HelplineTicket> findByRaisedByOrderByIdDesc(Long raisedBy);

    List<HelplineTicket> findAllByOrderByIdDesc();
}
