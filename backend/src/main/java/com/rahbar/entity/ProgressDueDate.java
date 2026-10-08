package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A date by which students must upload a progress report (Admin > Progress Due Dates). */
@Entity
@Table(name = "progress_due_dates")
@Getter @Setter
public class ProgressDueDate extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "due_id")
    private Long dueId;

    /** E.g. "Semester 1 results 2026". */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "due_date", nullable = false, unique = true)
    private LocalDate dueDate;

    @Column(name = "note", length = 500)
    private String note;
}
