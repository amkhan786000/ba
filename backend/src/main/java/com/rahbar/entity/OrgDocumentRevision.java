package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A document as it was before a change (created_by / created_at: who saved the change that replaced it). */
@Entity
@Table(name = "document_revisions")
@Getter @Setter
public class OrgDocumentRevision extends Modifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    private java.time.LocalDate effectiveDate;
}
