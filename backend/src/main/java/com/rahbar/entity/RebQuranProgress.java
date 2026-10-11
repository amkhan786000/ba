package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A day's Quran lesson of an REB girl (sabaq, sabqi or manzil). */
@Entity
@Table(name = "reb_quran_progress")
@Getter @Setter
public class RebQuranProgress extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private LocalDate date;

    /** SABAQ (new lesson), SABQI (recent revision) or MANZIL (older revision). */
    @Column(nullable = false, length = 10)
    private String kind = "SABAQ";

    /** 1-30. */
    private int para;

    @Column(length = 60)
    private String surah;

    private Integer fromAyah;

    private Integer toAyah;

    /** EXCELLENT, GOOD or NEEDS_REVISION. */
    @Column(nullable = false, length = 15)
    private String quality = "GOOD";

    /** She finished memorising this para with this lesson. */
    private boolean completedPara;

    @Column(length = 300)
    private String remark;

    private Long teacherStaffId;
}
