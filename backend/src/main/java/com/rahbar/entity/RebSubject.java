package com.rahbar.entity;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A subject of the Madrasa or School track. */
@Entity
@Table(name = "reb_subjects")
@Getter @Setter
public class RebSubject extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(length = 20)
    private String code;
    /** MADRASA, SCHOOL or BOTH. */
    @Column(nullable = false, length = 10)
    private String track = "SCHOOL";
    /** WRITTEN, ORAL or PRACTICAL. */
    @Column(nullable = false, length = 12)
    private String assessment = "WRITTEN";
    private boolean active = true;
}
