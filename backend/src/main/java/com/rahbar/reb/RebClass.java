package com.rahbar.reb;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A class level, kept across sessions (e.g. Class 6, Hifz, Alimiyat 1). */
@Entity
@Table(name = "reb_classes")
@Getter @Setter
public class RebClass extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    /** MADRASA, SCHOOL or BOTH. */
    @Column(nullable = false, length = 10)
    private String track = "BOTH";
    private int levelOrder;
    /** Where girls go when promoted (empty: the next active class by order). */
    private Long nextClassId;
    /** The Bihar Board exam class (e.g. Class 10). */
    private boolean boardExam;
    private boolean active = true;
}
