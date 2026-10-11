package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A holiday or event of an REB session (holidays are left out of attendance). */
@Entity
@Table(name = "reb_calendar")
@Getter @Setter
public class RebCalendarEvent extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private LocalDate dateFrom;

    @Column(nullable = false)
    private LocalDate dateTo;

    @Column(nullable = false, length = 150)
    private String title;

    /** HOLIDAY, EVENT or EXAM. */
    @Column(nullable = false, length = 10)
    private String kind = "HOLIDAY";
}
