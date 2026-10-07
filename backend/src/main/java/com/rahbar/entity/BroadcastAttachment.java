package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A file sent with a broadcast message (stored under the uploads folder with a random prefix). */
@Entity
@Table(name = "broadcast_attachments")
@Getter @Setter
public class BroadcastAttachment extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "broadcast_id", nullable = false)
    private Long broadcastId;

    /** Original name, as the recipients see it. */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** Stored name under the uploads folder. */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;
}
