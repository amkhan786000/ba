package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_institution_courses")
@Getter @Setter
public class StudentInstitutionCourse extends Modifiable {
    @Id
    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "institution_id", nullable = false, length = 50)
    private String institutionId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "assigned_by", length = 50)
    private String assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}
