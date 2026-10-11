package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A class in a session (e.g. Class 6 A). */
@Entity
@Table(name = "reb_sections")
@Getter @Setter
public class RebSection extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sessionId;
    @Column(nullable = false)
    private Long classId;
    @Column(nullable = false, length = 20)
    private String name = "A";
    private Long classTeacherStaffId;
    @Column(length = 40)
    private String room;
    private Integer capacity;
}
