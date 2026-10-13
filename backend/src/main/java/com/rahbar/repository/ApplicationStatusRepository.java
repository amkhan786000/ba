package com.rahbar.repository;

import com.rahbar.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Status history of scholarship applications (the current status is on the application itself). */
public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatus, Long> {

    List<ApplicationStatus> findByApplicationIdOrderByCreatedAtDescStatusIdDesc(Long applicationId);
}
