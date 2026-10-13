package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A file uploaded with (or after) a scholarship application: marksheet, ID proof, income certificate... */
@Entity
@Table(name = "application_documents")
@Getter @Setter
public class ApplicationDocument extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    /** The scholarship application (scholarship_applications.id). Rows from before V45 have grantee_detail_id instead. */
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "doc_type", length = 50)
    private String docType;

    /** Original file name as uploaded (for display). */
    @Column(name = "file_name")
    private String fileName;

    /** Stored file name under the uploads folder. */
    @Column(name = "file_path", nullable = false)
    private String filePath;
}
