package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A subject in the curriculum of a class for a session. */
@Entity
@Table(name = "reb_class_subjects")
@Getter @Setter
public class RebClassSubject extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sessionId;
    @Column(nullable = false)
    private Long classId;
    @Column(nullable = false)
    private Long subjectId;
    private int maxMarks = 100;
    private int passMarks = 33;
    private int displayOrder;
}
