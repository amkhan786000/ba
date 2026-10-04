package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "grantee_details")
@Getter @Setter
public class GranteeDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grantee_detail_id")
    private Long granteeDetailId;

    @Column(name = "user_id", length = 50)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "father_name")
    private String fatherName;

    @Column(name = "mother_name")
    private String motherName;

    @Column(name = "father_profession")
    private String fatherProfession;

    @Column(name = "mother_profession")
    private String motherProfession;

    @Column(name = "address")
    private String address;

    @Column(name = "average_annual_salary")
    private BigDecimal averageAnnualSalary;

    @Column(name = "rahbar_alumnus")
    private String rahbarAlumnus = "N";

    @Column(name = "rcc_name")
    private String rccName;

    @Column(name = "course_applied")
    private String courseApplied;

    @Column(name = "father_mobile")
    private String fatherMobile;

    @Column(name = "mother_mobile")
    private String motherMobile;

    @Column(name = "student_mobile")
    private String studentMobile;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
