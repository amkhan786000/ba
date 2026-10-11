package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A residential REB girl's leave from the hostel (gate pass). */
@Entity
@Table(name = "reb_hostel_leaves")
@Getter @Setter
public class RebHostelLeave extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String passNo;

    @Column(nullable = false)
    private Long studentId;

    /** The guardian who took her (must be allowed to). */
    private Long guardianId;

    @Column(nullable = false)
    private LocalDateTime leaveFrom;

    /** The day she is due back. */
    @Column(nullable = false)
    private LocalDate leaveTo;

    @Column(nullable = false, length = 300)
    private String reason;

    /** OUT or RETURNED. */
    @Column(nullable = false, length = 10)
    private String status = "OUT";

    private LocalDateTime returnedAt;

    /** Who brought her back. */
    @Column(length = 120)
    private String returnedWith;

    @Column(length = 500)
    private String note;
}
