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

    /** The file attached to this version (stored file name and name as uploaded), if any. */
    @Column(length = 255)
    private String attachmentPath;

    @Column(length = 255)
    private String attachmentName;
}
