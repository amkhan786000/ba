package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A scholarship application from the public form ("Apply for a scholarship"). It moves APPLIED -> IN_PROCESS ->
 * INTERVIEW -> ACCEPTED / REJECTED; accepting copies the applicant into users (Student) and grantee_details.
 */
@Entity
@Table(name = "scholarship_applications")
@Getter @Setter
public class ScholarshipApplication extends Modifiable {

    public static final String APPLIED = "APPLIED";
    public static final String IN_PROCESS = "IN_PROCESS";
    public static final String INTERVIEW = "INTERVIEW";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    /** F or M. */
    @Column(length = 1)
    private String sex;

    @Column(name = "student_mobile", nullable = false, length = 20)
    private String studentMobile;

    private String fatherName;
    @Column(length = 20)
    private String fatherMobile;
    private String fatherProfession;
    private String motherName;
    @Column(length = 20)
    private String motherMobile;
    private String motherProfession;

    @Column(columnDefinition = "TEXT")
    private String address;

    private BigDecimal averageAnnualSalary;

    /** "1" when the applicant studied at a Rahbar coaching centre. */
    @Column(length = 1, nullable = false)
    private String rahbarAlumnus = "0";

    private String rccName;
    private String courseApplied;

    @Column(nullable = false, length = 20)
    private String status = APPLIED;

    private LocalDateTime interviewAt;
    private String interviewVenue;

    @Column(length = 2000)
    private String staffNote;

    private LocalDateTime decidedAt;
    private Long decidedBy;

    /** The account made (or linked) when the application was accepted. */
    @Column(name = "student_user_id")
    private Long studentUserId;
}
