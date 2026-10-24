package com.rahbar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A coaching center (RCC) student of class 7 to 10, from the admission form. Not a Rahbar scholarship account. */
@Entity
@Table(name = "coaching_students")
@Getter @Setter
public class CoachingStudent extends Modifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "rcc_center_id", nullable = false)
    private Long rccCenterId;

    @Column(name = "admission_no", length = 30)
    private String admissionNo;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    /** 7, 8, 9 or 10. */
    @Column(name = "class_level", nullable = false)
    private Integer classLevel;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** M or F. */
    @Column(name = "gender", length = 1)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "school_name", length = 200)
    private String schoolName;

    /** Marks (%) in the previous class. */
    @Column(name = "previous_class_percent")
    private BigDecimal previousClassPercent;

    @Column(name = "father_name", length = 150)
    private String fatherName;

    @Column(name = "father_occupation", length = 100)
    private String fatherOccupation;

    @Column(name = "mother_name", length = 150)
    private String motherName;

    @Column(name = "guardian_mobile", length = 20)
    private String guardianMobile;

    @Column(name = "student_mobile", length = 20)
    private String studentMobile;

    /** Yearly family income (₹). */
    @Column(name = "family_income")
    private BigDecimal familyIncome;

    @Column(name = "address", length = 500)
    private String address;

    /** Only the last 4 digits of the Aadhaar number. */
    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;

    /** ACTIVE or LEFT. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "email", length = 150)
    private String email;

    /** Class 10: when the office invited them to apply for the Rahbar scholarship (null: not yet). */
    @Column(name = "invited_at")
    private java.time.LocalDateTime invitedAt;

    @Column(name = "invited_by")
    private Long invitedBy;

    /** How they were invited: CALL, EMAIL or OTHER. */
    @Column(name = "invite_method", length = 10)
    private String inviteMethod;

    /** Their Rahbar scholarship application (scholarship_applications.id), once they applied. */
    @Column(name = "application_id")
    private Long applicationId;
}
