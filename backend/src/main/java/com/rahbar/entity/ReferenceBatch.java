package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Students > Reference Numbers: one generation for one prefix, e.g. Patna Chapter got PVT001-2026 to PVT040-2026. */
@Entity
@Table(name = "reference_batches")
@Getter @Setter
public class ReferenceBatch extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer sessionYear;

    /** The chapter numbered (with allChapters false: null = students without a chapter). */
    private Long chapterId;

    private String chapterName;

    /** Every chapter at once. */
    @Column(nullable = false)
    private boolean allChapters;

    @Column(nullable = false, length = 5)
    private String prefix;

    @Column(nullable = false)
    private Integer firstNumber;

    @Column(nullable = false)
    private Integer lastNumber;

    @Column(nullable = false)
    private Integer howMany;
}
