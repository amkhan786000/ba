package com.rahbar.service;

import com.rahbar.entity.ApplicationStatus;
import com.rahbar.entity.GranteeDetails;
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
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final ApplicationStatusRepository applicationStatusRepository;

    public PublicService(ApplicationPeriodRepository applicationPeriodRepository,
                         RccCenterRepository rccCenterRepository, CourseRepository courseRepository,
                         GranteeDetailsRepository granteeDetailsRepository,
                         ApplicationStatusRepository applicationStatusRepository) {
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.courseRepository = courseRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
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

    /** Saves a new application and its first "submitted" status; returns the application id. */
    public Long apply(Map<String, Object> form) {
        if (!isApplicationPeriodOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Applications are currently closed.");
        }

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

        GranteeDetails gd = new GranteeDetails();
        gd.setName(str(form.get("name")));
        gd.setFatherName(str(form.get("fatherName")));
        gd.setMotherName(str(form.get("motherName")));
        gd.setFatherProfession(str(form.get("fatherProfession")));
        gd.setMotherProfession(str(form.get("motherProfession")));
        gd.setAddress(str(form.get("address")));
        Object salary = form.get("averageAnnualSalary");
        gd.setAverageAnnualSalary(isBlank(salary) ? null : new BigDecimal(String.valueOf(salary).trim()));
        gd.setRahbarAlumnus(Boolean.TRUE.equals(form.get("rahbarAlumnus")) ? "1" : "0");
        gd.setRccName(str(form.get("rccName")));
        gd.setCourseApplied(str(form.get("courseApplied")));
        gd.setFatherMobile(fatherMobile);
        gd.setMotherMobile(motherMobile);
        gd.setStudentMobile(studentMobile);
        gd = granteeDetailsRepository.save(gd);

        ApplicationStatus status = new ApplicationStatus();
        status.setGranteeDetailId(gd.getGranteeDetailId());
        status.setStatus("submitted");
        status.setComments("Application submitted");
        applicationStatusRepository.save(status);

        return gd.getGranteeDetailId();
    }

    /** The application plus its latest status, comments and status_date. */
    public Map<String, Object> applicationStatus(Long applicationId) {
        GranteeDetails gd = granteeDetailsRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid application ID"));
        Map<String, Object> row = Rows.of(gd);
        ApplicationStatus latest = applicationStatusRepository.findFirstByGranteeDetailIdOrderByCreatedAtDesc(applicationId).orElse(null);
        row.put("status", latest == null ? null : latest.getStatus());
        row.put("comments", latest == null ? null : latest.getComments());
        row.put("status_date", latest == null ? null : latest.getCreatedAt());
        return row;
    }

    public Long findApplicationIdByMobile(String mobile) {
        List<GranteeDetails> found = granteeDetailsRepository.findByStudentMobileOrFatherMobileOrMotherMobile(mobile, mobile, mobile);
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No application found with this mobile number");
        }
        return found.get(0).getGranteeDetailId();
    }
}
