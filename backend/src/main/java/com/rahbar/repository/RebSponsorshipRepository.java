package com.rahbar.repository;

import com.rahbar.entity.RebSponsorship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebSponsorshipRepository extends JpaRepository<RebSponsorship, Long> {

    List<RebSponsorship> findAllByOrderByIdDesc();

    List<RebSponsorship> findByStudentIdOrderByStartPeriodDesc(Long studentId);

    List<RebSponsorship> findByStatus(String status);
}
