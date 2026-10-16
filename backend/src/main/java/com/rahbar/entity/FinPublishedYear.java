package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A year whose income & expense summary is shown on the website. */
@Entity
@Table(name = "fin_published_years")
@Getter @Setter
public class FinPublishedYear extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** REB (Rahbar's own book was removed in V48): each book publishes its own years. */
    @Column(nullable = false, length = 10)
    private String book = "REB";

    @Column(nullable = false)
    private Integer year;

    @Column(length = 500)
    private String note;
}
