package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * An organisation document (by-laws, policies, ...) kept as text: pasted in, not uploaded. Admin > Documents.
 * Named OrgDocument so it doesn't clash with the PDF library's Document class.
 */
@Entity
@Table(name = "documents")
@Getter @Setter
public class OrgDocument extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String title;

    /** The text as pasted; line breaks are kept and Markdown (## heading, **bold**, lists) is shown formatted. */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** When the document (or this version of it) took effect, e.g. the date by-laws were adopted. */
    private java.time.LocalDate effectiveDate;

    /** Kept but hidden from the normal list. */
    private boolean archived;
}
