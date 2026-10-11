package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One subject of one class in an REB exam (a line of the date sheet). */
@Entity
@Table(name = "reb_exam_papers")
@Getter @Setter
public class RebExamPaper extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long examId;

    @Column(nullable = false)
    private Long classId;

    @Column(nullable = false)
    private Long subjectId;

    private LocalDate examDate;

    private int maxMarks = 100;

    private int passMarks = 33;
}
