package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_schedules")
@Getter @Setter
public class PaymentSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "deadline_date")
    private LocalDate deadlineDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
