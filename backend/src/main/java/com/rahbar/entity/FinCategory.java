package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A category of income or expense (Administration > Income & Expenses). */
@Entity
@Table(name = "fin_categories")
@Getter @Setter
public class FinCategory extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Whose books: REB (Rahbar's own book was removed in V48) (never shown in the other's screens). */
    @Column(nullable = false, length = 10)
    private String book = "REB";

    @Column(nullable = false, length = 100)
    private String name;

    /** INCOME or EXPENSE. */
    @Column(nullable = false, length = 10)
    private String kind;

    private boolean active = true;
}
