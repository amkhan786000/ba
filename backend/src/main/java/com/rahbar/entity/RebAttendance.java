package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A girl's attendance on a day. */
@Entity
@Table(name = "reb_attendance")
@Getter @Setter
public class RebAttendance extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long sectionId;

    @Column(nullable = false)
    private LocalDate date;

    /** PRESENT, ABSENT, LATE or LEAVE. */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(length = 200)
    private String remark;
}
