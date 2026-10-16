package com.rahbar.repository;

import com.rahbar.entity.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, Long> {
    Optional<PaymentSchedule> findByYearAndCollegeType(Integer year, String collegeType);
    List<PaymentSchedule> findByYearAndStatus(Integer year, Integer status);
    List<PaymentSchedule> findAllByOrderByYearDesc();
}
