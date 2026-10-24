package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A sponsor renewal campaign: asking every active sponsor whether they will continue in a session year. */
@Entity
@Table(name = "renewal_campaigns")
@Getter @Setter
public class RenewalCampaign extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** The session the sponsors are asked about (e.g. 2027). */
    @Column(name = "session_year", nullable = false)
    private Integer sessionYear;

    /** Please answer by. */
    @Column(name = "deadline")
    private LocalDate deadline;

    /** The office's own words, added to the email and the portal page. */
    @Column(name = "message", length = 2000)
    private String message;

    /** Closed: answers are kept but can't change. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
