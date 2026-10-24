package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An event or workshop for an audience; a chapter or a coaching center may run its own. */
@Entity
@Table(name = "events")
@Getter @Setter
public class Event extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** WORKSHOP, CAREER, PARENTS, EXAM_PREP, CEREMONY or OTHER. */
    @Column(name = "kind", nullable = false, length = 15)
    private String kind;

    @Column(name = "description", length = 3000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private java.time.LocalDateTime startsAt;

    @Column(name = "ends_at")
    private java.time.LocalDateTime endsAt;

    @Column(name = "venue", length = 300)
    private String venue;

    @Column(name = "online_link", length = 500)
    private String onlineLink;

    /** SCHOLARS, COACHING, SPONSORS, PARENTS or ALL. */
    @Column(name = "audience", nullable = false, length = 10)
    private String audience;

    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "rcc_center_id")
    private Long rccCenterId;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "registration_open", nullable = false)
    private boolean registrationOpen = true;

    /** SCHEDULED, CANCELLED or DONE. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "SCHEDULED";
}
