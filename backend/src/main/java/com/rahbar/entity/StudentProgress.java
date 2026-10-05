package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_progress")
@Getter @Setter
public class StudentProgress extends Modifiable {
    @Id
    // Assigned as MAX + 1 by the services (see the repository nextId())
    @Column(name = "progress_id")
    private Long progressId;

    @Column(name = "grantee_id", nullable = false, length = 50)
    private String granteeId;

    @Column(name = "marks")
    private String marks;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "session", nullable = false)
    private String session = "0";

    @Column(name = "year", nullable = false)
    private Integer year;

    /** Pending / Approved / Rejected, set by the sponsor, convenor or an admin. */
    @Column(name = "review_status")
    private String reviewStatus = "Pending";

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;

}
