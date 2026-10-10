package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payment_schedules")
@Getter @Setter
public class PaymentSchedule extends Modifiable {
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

    /** An installment is due every this many months (3 or 4); the amount is per installment. */
    @Column(name = "frequency_months", nullable = false)
    @org.hibernate.annotations.ColumnDefault("3")
    private Integer frequencyMonths = 3;

    /** An installment shows as "Due" this many days before its due date (and as "Overdue" after it). */
    @Column(name = "due_notice_days", nullable = false)
    @org.hibernate.annotations.ColumnDefault("30")
    private Integer dueNoticeDays = 30;

}
