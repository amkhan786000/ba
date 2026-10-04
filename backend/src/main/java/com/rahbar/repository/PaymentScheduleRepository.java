package com.rahbar.repository;

import com.rahbar.entity.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, Long> {
    Optional<PaymentSchedule> findByYear(Integer year);
    List<PaymentSchedule> findAllByOrderByYearDesc();
}
