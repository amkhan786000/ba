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

/** Public scholarship application form and application-status lookup (no login). */
@Service
public class PublicService {

    private final ApplicationPeriodRepository applicationPeriodRepository;
    private final RccCenterRepository rccCenterRepository;
    private final CourseRepository courseRepository;
    private final ScholarshipApplicationRepository applications;
    private final ApplicationStatusRepository applicationStatusRepository;

    public PublicService(ApplicationPeriodRepository applicationPeriodRepository,
                         RccCenterRepository rccCenterRepository, CourseRepository courseRepository,
                         ScholarshipApplicationRepository applications,
                         ApplicationStatusRepository applicationStatusRepository) {
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.courseRepository = courseRepository;
        this.applications = applications;
        this.applicationStatusRepository = applicationStatusRepository;
    }

    /** True when an active application period covers today. */
    public boolean isApplicationPeriodOpen() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        return applicationPeriodRepository.existsByIsActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(today, today);
    }

    public Map<String, Object> formOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodOpen", isApplicationPeriodOpen());
        result.put("rccCenters", Rows.list(rccCenterRepository.findAll()));
        result.put("courses", Rows.list(courseRepository.findAll()));
        return result;
    }

    /** Saves a new scholarship application (status Applied) and its first history row; returns the application id. */
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
        a.setStatus(ScholarshipApplication.APPLIED);
        a = applications.save(a);

        ApplicationStatus status = new ApplicationStatus();
        status.setApplicationId(a.getId());
        status.setStatus(ScholarshipApplication.APPLIED);
        status.setComments("Application submitted");
        applicationStatusRepository.save(status);

        return a.getId();
    }

    /** The application plus its status and when it last changed. */
    public Map<String, Object> applicationStatus(Long applicationId) {
        ScholarshipApplication a = applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid application ID"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("name", a.getName());
        row.put("course_applied", a.getCourseApplied());
        List<ApplicationStatus> history = applicationStatusRepository.findByApplicationIdOrderByCreatedAtDescStatusIdDesc(applicationId);
        row.put("status", ApplicationService.label(a.getStatus()));
        row.put("comments", history.isEmpty() ? null : history.get(0).getComments());
        row.put("status_date", history.isEmpty() ? a.getCreatedAt() : history.get(0).getCreatedAt());
        return row;
    }

    public Long findApplicationIdByMobile(String mobile) {
        List<ScholarshipApplication> found = applications.findByStudentMobileOrFatherMobileOrMotherMobileOrderByIdDesc(mobile, mobile, mobile);
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No application found with this mobile number");
        }
        return found.get(0).getId();
    }
}
