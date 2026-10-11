package com.rahbar.reb;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A girl in a section for a session (one per session). */
@Entity
@Table(name = "reb_enrollments")
@Getter @Setter
public class RebEnrollment extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long studentId;
    @Column(nullable = false)
    private Long sessionId;
    @Column(nullable = false)
    private Long sectionId;
    @Column(length = 10)
    private String rollNo;
    /** ACTIVE, PROMOTED, DETAINED, LEFT or COMPLETED. */
    @Column(nullable = false, length = 12)
    private String status = "ACTIVE";
}
