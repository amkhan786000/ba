package com.rahbar.repository;

import com.rahbar.entity.Mentorship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorshipRepository extends JpaRepository<Mentorship, Long> {
    List<Mentorship> findAllByOrderByStatusAscStartedOnDescIdDesc();

    List<Mentorship> findByStatus(String status);
}
