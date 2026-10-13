package com.rahbar.service;

import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GranteeDetailsRepository;
import com.rahbar.repository.ScholarshipApplicationRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Accepting a scholarship application: the applicant becomes a student. A Student account is made from the
 * application (temporary password emailed, as in Manage Users) and their family details are copied into the
 * student's details (grantee_details). Someone who already has an account with that email (e.g. a former applicant
 * or a staff member) gets the Student role added instead of a second account. Sponsor, college / course and payment
 * details are completed on the student's profile afterwards.
 */
@Service
public class ApplicationAcceptService {

    private static final int STUDENT_ROLE = 6;

    private final ScholarshipApplicationRepository applications;
    private final ApplicationService applicationService;
    private final AdminService adminService;
    private final AdditionalRoleService additionalRoles;
    private final UserRepository users;
    private final GranteeDetailsRepository granteeDetails;

    public ApplicationAcceptService(ScholarshipApplicationRepository applications, ApplicationService applicationService,
                                    AdminService adminService, AdditionalRoleService additionalRoles, UserRepository users,
                                    GranteeDetailsRepository granteeDetails) {
        this.applications = applications;
        this.applicationService = applicationService;
        this.adminService = adminService;
        this.additionalRoles = additionalRoles;
        this.users = users;
        this.granteeDetails = granteeDetails;
    }

    /** Accepts the application and makes (or links) the student. Returns a message, and the temporary password when it could not be emailed. */
    @Transactional
    public Map<String, Object> accept(Long applicationId, String staffNote) {
        ScholarshipApplication a = applicationService.require(applicationId);
        if (ScholarshipApplication.ACCEPTED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This application was accepted already.");
        }
        if (ScholarshipApplication.REJECTED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This application was rejected. Move it back to \"In process\" first if that was a mistake.");
        }
        String email = a.getEmail() == null ? "" : a.getEmail().trim();
        if (!email.contains("@")) throw new ApiException(HttpStatus.BAD_REQUEST, "The application has no valid email, so no account can be made.");

        Map<String, Object> result = new LinkedHashMap<>();
        User student = users.findByEmail(email).orElse(null);
        if (student != null) {
            // Same person, existing account: add the Student role (one account per person).
            if (student.getRoleId() != STUDENT_ROLE && !additionalRoles.of(student.getId()).contains(STUDENT_ROLE)) {
                List<Integer> roles = new ArrayList<>(additionalRoles.of(student.getId()));
                roles.add(STUDENT_ROLE);
                additionalRoles.set(student, roles);
            }
            if (student.getStudyStatus() == null) student.setStudyStatus(StudyStatus.STUDYING);
            users.save(student);
            result.put("message", "Accepted. " + email + " already had an account (" + student.getUserId()
                    + "), which is now this student; no new password was sent.");
        } else {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("roleId", STUDENT_ROLE);
            body.put("name", a.getName());
            body.put("email", email);
            // The student's mobile, unless another account uses it already.
            if (a.getStudentMobile() != null && users.findByPhone(a.getStudentMobile()).isEmpty()) body.put("contact", a.getStudentMobile());
            body.put("sex", "F".equals(a.getSex()) ? "F" : "M");
            body.put("status", "Active");
            Map<String, Object> created = adminService.createUser(body);
            student = users.findByEmail(email).orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The student account could not be found after creating it."));
            result.put("message", "Accepted. Student " + student.getUserId() + " was created. " + created.get("message"));
            if (created.get("temporaryPassword") != null) result.put("temporaryPassword", created.get("temporaryPassword"));
        }

        // The student's details (family, income, address, RCC) start from the application.
        GranteeDetails gd = granteeDetails.findFirstByUserIdOrderByGranteeDetailIdAsc(student.getId()).orElseGet(GranteeDetails::new);
        gd.setUserId(student.getId());
        gd.setName(a.getName());
        gd.setStudentMobile(a.getStudentMobile());
        gd.setFatherName(a.getFatherName());
        gd.setFatherMobile(a.getFatherMobile());
        gd.setFatherProfession(a.getFatherProfession());
        gd.setMotherName(a.getMotherName());
        gd.setMotherMobile(a.getMotherMobile());
        gd.setMotherProfession(a.getMotherProfession());
        gd.setAddress(a.getAddress());
        gd.setAverageAnnualSalary(a.getAverageAnnualSalary());
        gd.setRahbarAlumnus(a.getRahbarAlumnus());
        gd.setRccName(a.getRccName());
        gd.setCourseApplied(a.getCourseApplied());
        gd.setInterviewAt(a.getInterviewAt());
        gd.setInterviewVenue(a.getInterviewVenue());
        granteeDetails.save(gd);

        String note = staffNote == null || staffNote.isBlank() ? null : staffNote.trim();
        if (note != null && note.length() > 2000) throw new ApiException(HttpStatus.BAD_REQUEST, "The note can be at most 2000 characters.");
        a.setStaffNote(note);
        a.setStudentUserId(student.getId());
        a.setDecidedAt(LocalDateTime.now());
        a.setDecidedBy(Access.current().getUser().getId());
        applicationService.setStatus(a, ScholarshipApplication.ACCEPTED, note);
        applications.save(a);
        applicationService.notifyApplicant(a, null);

        result.put("studentId", student.getId());
        result.put("studentCode", student.getUserId());
        return result;
    }
}
