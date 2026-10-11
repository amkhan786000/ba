package com.rahbar.reb;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Who teaches a subject in a section. */
@Entity
@Table(name = "reb_teacher_assignments")
@Getter @Setter
public class RebTeacherAssignment extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sessionId;
    @Column(nullable = false)
    private Long sectionId;
    @Column(nullable = false)
    private Long subjectId;
    @Column(nullable = false)
    private Long staffId;
}
