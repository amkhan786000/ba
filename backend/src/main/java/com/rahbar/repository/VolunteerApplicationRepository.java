package com.rahbar.repository;

import com.rahbar.entity.VolunteerApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VolunteerApplicationRepository extends JpaRepository<VolunteerApplication, Long> {

    List<VolunteerApplication> findAllByOrderByCreatedAtDesc();

    /** Sign-ups with this email or phone since the given time (repeat / flood check). */
    long countByEmailIgnoreCaseAndCreatedAtAfterOrPhoneAndCreatedAtAfter(String email, LocalDateTime since1,
                                                                          String phone, LocalDateTime since2);

    long countByStatus(String status);
}
