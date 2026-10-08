package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One change of a student's study status (see service.StudyStatus). */
@Entity
@Table(name = "student_status_history")
@Getter @Setter
public class StudentStatusHistory extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "note", length = 500)
    private String note;
}
