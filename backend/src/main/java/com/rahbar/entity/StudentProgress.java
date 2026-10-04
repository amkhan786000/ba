package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_progress")
@Getter @Setter
public class StudentProgress extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

}
