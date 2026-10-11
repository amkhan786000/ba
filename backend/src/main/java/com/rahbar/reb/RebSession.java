package com.rahbar.reb;

import com.rahbar.entity.Modifiable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An academic session of REB (its own start and end dates). */
@Entity
@Table(name = "reb_sessions")
@Getter @Setter
public class RebSession extends Modifiable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String name;
    @Column(nullable = false)
    private java.time.LocalDate startDate;
    @Column(nullable = false)
    private java.time.LocalDate endDate;
    @Column(name = "is_current", nullable = false)
    private boolean current;
    /** OPEN or CLOSED. */
    @Column(nullable = false, length = 10)
    private String status = "OPEN";
}
