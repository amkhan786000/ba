package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Something Rahbar hands out to students: a book, uniform, laptop, bicycle... */
@Entity
@Table(name = "material_items")
@Getter @Setter
public class MaterialItem extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** BOOK, UNIFORM, STATIONERY, DEVICE, BICYCLE or OTHER. */
    @Column(name = "category", nullable = false, length = 12)
    private String category;

    /** e.g. "set", "pair"; empty: pieces. */
    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
