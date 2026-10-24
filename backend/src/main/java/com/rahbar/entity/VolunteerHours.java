package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Hours a volunteer, mentor or alumnus gave on a day. */
@Entity
@Table(name = "volunteer_hours")
@Getter @Setter
public class VolunteerHours extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** A website volunteer sign-up, or */
    @Column(name = "volunteer_id")
    private Long volunteerId;

    /** a Rahbar user (mentor, alumnus, sponsor...). */
    @Column(name = "user_id")
    private Long userId;

    /** MENTORING, TUTORING, EVENT, CHAPTER, COACHING, OFFICE or OTHER. */
    @Column(name = "activity", nullable = false, length = 12)
    private String activity;

    @Column(name = "worked_on", nullable = false)
    private java.time.LocalDate workedOn;

    @Column(name = "hours", nullable = false)
    private java.math.BigDecimal hours;

    @Column(name = "description", length = 500)
    private String description;

    /** APPROVED, PENDING (self-logged, to approve) or REJECTED. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "APPROVED";

    @Column(name = "self_logged", nullable = false)
    private boolean selfLogged = false;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;
}
