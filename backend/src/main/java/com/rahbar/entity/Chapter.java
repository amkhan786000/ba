package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A regional chapter. Users point at it through users.chapter_id (optional). */
@Entity
@Table(name = "chapters")
@Getter @Setter
public class Chapter extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chapter_id")
    private Long chapterId;

    @Column(name = "chapter_name", nullable = false, unique = true, length = 100)
    private String chapterName;

    @Column(name = "description")
    private String description;

    @Column(name = "lead_name", length = 100)
    private String leadName;

    @Column(name = "lead_phone", length = 30)
    private String leadPhone;

    @Column(name = "lead_email", length = 150)
    private String leadEmail;

    /** Inactive chapters stay on existing users but are no longer offered in the chapter dropdowns. */
    @Column(name = "active", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1") // existing rows become active when the column is added
    private Boolean active = true;

}
