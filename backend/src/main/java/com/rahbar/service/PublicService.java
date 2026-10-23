package com.rahbar.service;

import com.rahbar.entity.ApplicationStatus;
import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.ServiceSupport.isBlank;
import static com.rahbar.service.ServiceSupport.str;

/** Scholarship applications from the website (see WebsiteScholarshipController) and the application-status lookup (no login). */
@Service
public class PublicService {

    private final ApplicationPeriodRepository applicationPeriodRepository;
    private final ScholarshipApplicationRepository applications;
    private final ApplicationStatusRepository applicationStatusRepository;

    public PublicService(ApplicationPeriodRepository applicationPeriodRepository,
                         ScholarshipApplicationRepository applications,
                         ApplicationStatusRepository applicationStatusRepository) {
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.applications = applications;
        this.applicationStatusRepository = applicationStatusRepository;
    }

    /** True when an active application period covers today. */
    public boolean isApplicationPeriodOpen() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        return applicationPeriodRepository.existsByIsActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);
    }

    /** Saves a new scholarship application (status Applied) and its first history row; returns the application id. */
    /**
     * The 2026 form's sections. Required: date of birth, 10th year and %, all bank details and both declarations
     * (terms and self-declaration); the rest is optional. Aadhaar: only the last 4 digits.
     */
    static void applyForm2026(ScholarshipApplication a, Map<String, Object> form) {
        String by = upper(form.get("recommendedBy"));
        if (by != null && !by.equals("RCC") && !by.equals("CHAPTER")) throw bad("Choose who recommended the student: an RCC or a BA chapter.");
        a.setRecommendedBy(by);
        a.setRecommendedChapter("CHAPTER".equals(by) ? text(form.get("recommendedChapter"), 150) : null);
        if ("CHAPTER".equals(by)) a.setRccName(null);
        a.setInchargeName(text(form.get("inchargeName"), 120));
        a.setInchargeMobile(optionalMobile(form.get("inchargeMobile"), "The incharge's mobile"));

        String seat = upper(form.get("govtSeat"));
        if (seat != null && !seat.equals("Y") && !seat.equals("N")) throw bad("Answer yes or no about a seat in a Govt. college.");
        a.setGovtSeat(seat);
        a.setGovtSeatDetails("Y".equals(seat) ? text(form.get("govtSeatDetails"), 500) : null);

        String aadhaar = text(form.get("aadhaarLast4"), 10);
        if (aadhaar != null && !aadhaar.matches("\\d{4}")) throw bad("Enter only the last 4 digits of the Aadhaar number.");
        a.setAadhaarLast4(aadhaar);

        String dob = text(form.get("dateOfBirth"), 10);
        if (dob == null) throw bad("Enter the student's date of birth.");
        java.time.LocalDate birth;
        try { birth = java.time.LocalDate.parse(dob); } catch (java.time.format.DateTimeParseException e) { throw bad("Enter the date of birth as a date."); }
        int age = java.time.Period.between(birth, java.time.LocalDate.now()).getYears();
        if (age < 12 || age > 40) throw bad("Please check the date of birth.");
        a.setDateOfBirth(birth);
        String nationality = text(form.get("nationality"), 60);
        a.setNationality(nationality == null ? "Indian" : nationality);

        a.setEdu10Year(year(form.get("edu10Year"), "10th: year of passing", true));
        a.setEdu10Percent(percent(form.get("edu10Percent"), "10th: percentage", true));
        a.setEdu12Year(year(form.get("edu12Year"), "10+2: year of passing", false));
        a.setEdu12Percent(percent(form.get("edu12Percent"), "10+2: percentage", false));
        a.setItiYear(year(form.get("itiYear"), "ITI: year of passing", false));
        a.setItiPercent(percent(form.get("itiPercent"), "ITI: percentage", false));
        a.setDiplomaYear(year(form.get("diplomaYear"), "Diploma: year of passing", false));
        a.setDiplomaPercent(percent(form.get("diplomaPercent"), "Diploma: percentage", false));

        a.setGuardianName(text(form.get("guardianName"), 120));
        a.setGuardianMobile(optionalMobile(form.get("guardianMobile"), "The guardian's mobile"));

        String holder = text(form.get("bankAccountHolder"), 120), number = text(form.get("bankAccountNumber"), 30),
                ifsc = upper(form.get("bankIfsc")), bank = text(form.get("bankName"), 120), branch = text(form.get("bankBranch"), 120);
        if (holder == null || number == null || ifsc == null || bank == null || branch == null) {
            throw bad("Enter all the bank details: account holder, account number, IFSC, bank and branch.");
        }
        if (!number.replaceAll("\\s", "").matches("\\d{6,20}")) throw bad("The account number should be 6 to 20 digits.");
        if (!ifsc.matches("[A-Z]{4}0[A-Z0-9]{6}")) throw bad("The IFSC code should look like SBIN0001234.");
        a.setBankAccountHolder(holder);
        a.setBankAccountNumber(number.replaceAll("\\s", ""));
        a.setBankIfsc(ifsc);
        a.setBankName(bank);
        a.setBankBranch(branch);

        if (!Boolean.TRUE.equals(form.get("termsAccepted")) || !Boolean.TRUE.equals(form.get("selfDeclaration"))) {
            throw bad("Please read and accept the terms and the self-declaration.");
        }
        a.setDeclarationsAcceptedAt(java.time.LocalDateTime.now());
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String text(Object v, int max) {
        if (v == null) return null;
        String t = String.valueOf(v).strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String upper(Object v) {
        String t = text(v, 40);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    private static String optionalMobile(Object v, String what) {
        String t = text(v, 20);
        if (t == null) return null;
        String d = t.replaceAll("\\D", "");
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        if (!d.matches("\\d{10}")) throw bad(what + " must be 10 digits.");
        return d;
    }

    private static Integer year(Object v, String what, boolean required) {
        String t = text(v, 10);
        if (t == null) { if (required) throw bad("Enter " + what + "."); return null; }
        try {
            int y = Integer.parseInt(t);
            if (y < 1990 || y > java.time.LocalDate.now().getYear()) throw bad(what + " looks wrong.");
            return y;
        } catch (NumberFormatException e) { throw bad(what + " should be a year, e.g. 2025."); }
    }

    private static BigDecimal percent(Object v, String what, boolean required) {
        String t = text(v, 10);
        if (t == null) { if (required) throw bad("Enter " + what + "."); return null; }
        try {
            BigDecimal p = new BigDecimal(t.replace("%", "").trim());
            if (p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0) throw bad(what + " should be between 0 and 100.");
            return p.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) { throw bad(what + " should be a number, e.g. 78.5."); }
    }

    public Long apply(Map<String, Object> form) {
        if (!isApplicationPeriodOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Applications are currently closed.");
        }
        String name = str(form.get("name"));
        if (name == null || name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter the student's name.");
        String email = str(form.get("email"));
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a valid email address: the student's sign-in details are sent there.");
        }
        String sex = str(form.get("sex"));
        if (!"F".equals(sex) && !"M".equals(sex)) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose female or male.");

        String fatherMobile = str(form.get("fatherMobile"));
        String motherMobile = str(form.get("motherMobile"));
        String studentMobile = str(form.get("studentMobile"));
        List<String> mobiles = Arrays.asList(fatherMobile, motherMobile, studentMobile);
        if (new HashSet<>(mobiles).size() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "All three mobile numbers must be different");
        }
        for (String m : mobiles) {
            if (m == null || !m.matches("\\d{10}")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid mobile numbers. Must be 10 digits");
            }
        }

        ScholarshipApplication a = new ScholarshipApplication();
        a.setName(name.trim());
        a.setEmail(email);
        a.setSex(sex);
        a.setFatherName(str(form.get("fatherName")));
        a.setMotherName(str(form.get("motherName")));
        a.setFatherProfession(str(form.get("fatherProfession")));
        a.setMotherProfession(str(form.get("motherProfession")));
        a.setAddress(str(form.get("address")));
        Object salary = form.get("averageAnnualSalary");
        a.setAverageAnnualSalary(isBlank(salary) ? null : new BigDecimal(String.valueOf(salary).trim()));
        a.setRahbarAlumnus(Boolean.TRUE.equals(form.get("rahbarAlumnus")) ? "1" : "0");
        a.setRccName(str(form.get("rccName")));
        a.setCourseApplied(str(form.get("courseApplied")));
        a.setFatherMobile(fatherMobile);
        a.setMotherMobile(motherMobile);
        a.setStudentMobile(studentMobile);
        applyForm2026(a, form);
        a.setStatus(ScholarshipApplication.APPLIED);
        a = applications.save(a);

        ApplicationStatus status = new ApplicationStatus();
        status.setApplicationId(a.getId());
        status.setStatus(ScholarshipApplication.APPLIED);
        status.setComments("Application submitted");
        applicationStatusRepository.save(status);

        return a.getId();
    }
}
