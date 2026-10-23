package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A (monthly) exam of one class at one coaching center. */
@Entity
@Table(name = "coaching_exams")
@Getter @Setter
public class CoachingExam extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rcc_center_id", nullable = false)
    private Long rccCenterId;

    @Column(name = "class_level", nullable = false)
    private Integer classLevel;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    /** When the 1st / 2nd / 3rd prizes were last worked out (null: not yet). */
    @Column(name = "prizes_generated_at")
    private LocalDateTime prizesGeneratedAt;
}
