package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Students > Reference Numbers: the last number given for a session year and prefix (M = Govt, PVT = the rest). */
@Entity
@Table(name = "reference_counters")
@Getter @Setter
public class ReferenceCounter extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer sessionYear;

    @Column(nullable = false, length = 5)
    private String prefix;

    @Column(nullable = false)
    private Integer lastNumber;
}
