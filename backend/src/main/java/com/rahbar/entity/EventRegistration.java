package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Someone coming to an event: a user, a coaching student, or a guest by name and phone. */
@Entity
@Table(name = "event_registrations")
@Getter @Setter
public class EventRegistration extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "coaching_student_id")
    private Long coachingStudentId;

    @Column(name = "guest_name", length = 150)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "self_registered", nullable = false)
    private boolean selfRegistered = false;

    /** null: not marked yet. */
    @Column(name = "attended")
    private Boolean attended;
}
