package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One coaching student's attendance on one day: P (present), A (absent) or L (leave). */
@Entity
@Table(name = "coaching_attendance")
@Getter @Setter
public class CoachingAttendance extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rcc_center_id", nullable = false)
    private Long rccCenterId;

    @Column(name = "class_level", nullable = false)
    private Integer classLevel;

    @Column(name = "att_date", nullable = false)
    private LocalDate attDate;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "status", nullable = false, length = 1)
    private String status;
}
