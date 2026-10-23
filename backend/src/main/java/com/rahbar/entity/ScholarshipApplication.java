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

    /** Recommended by: RCC or CHAPTER (a BA chapter). */
    @Column(name = "recommended_by", length = 10)
    private String recommendedBy;

    /** The BA chapter that recommended the student (when recommended by a chapter; the RCC is rccName). */
    @Column(name = "recommended_chapter", length = 150)
    private String recommendedChapter;

    /** Name of the RCC incharge / BA chapter incharge. */
    @Column(name = "incharge_name", length = 120)
    private String inchargeName;

    @Column(name = "incharge_mobile", length = 20)
    private String inchargeMobile;

    /** Y / N: secured a seat, or appearing in a competitive exam for a seat, in a Govt. college. */
    @Column(name = "govt_seat", length = 1)
    private String govtSeat;

    /** If yes: course, college and university. */
    @Column(name = "govt_seat_details", length = 500)
    private String govtSeatDetails;

    /** Only the last 4 digits of the Aadhaar number (the self-attested copy is uploaded). */
    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "nationality", length = 60)
    private String nationality;

    /** Education: year of passing and % for 10th, 10+2, ITI and Diploma. */
    @Column(name = "edu_10_year")
    private Integer edu10Year;

    @Column(name = "edu_10_percent", precision = 5, scale = 2)
    private BigDecimal edu10Percent;

    @Column(name = "edu_12_year")
    private Integer edu12Year;

    @Column(name = "edu_12_percent", precision = 5, scale = 2)
    private BigDecimal edu12Percent;

    @Column(name = "iti_year")
    private Integer itiYear;

    @Column(name = "iti_percent", precision = 5, scale = 2)
    private BigDecimal itiPercent;

    @Column(name = "diploma_year")
    private Integer diplomaYear;

    @Column(name = "diploma_percent", precision = 5, scale = 2)
    private BigDecimal diplomaPercent;

    @Column(name = "guardian_name", length = 120)
    private String guardianName;

    @Column(name = "guardian_mobile", length = 20)
    private String guardianMobile;

    /** Bank details from the form; copied to the student's bank details on Accept. */
    @Column(name = "bank_account_holder", length = 120)
    private String bankAccountHolder;

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    /** When the applicant ticked the terms and the self-declaration. */
    @Column(name = "declarations_accepted_at")
    private LocalDateTime declarationsAcceptedAt;

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
