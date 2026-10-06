package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A regional chapter. A user's chapter is stored by name in users.region (optional). */
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

}
