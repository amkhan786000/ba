package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Admin > Chapter Dashboard: one chapter's students, sponsors, payments due and open applications.
 * Users whose role covers only their own chapter always see that chapter; others choose one.
 */
@Service
public class ChapterDashboardService {


    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final StudentInstitutionCourseRepository courseAssignmentRepository;
    private final CourseRepository courseRepository;
    private final InstitutionRepository institutionRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final ScholarshipApplicationRepository scholarshipApplications;
    private final PaymentReminderService paymentReminderService;

    public ChapterDashboardService(ChapterRepository chapterRepository, UserRepository userRepository,
                                   GrantorGranteeRepository grantorGranteeRepository,
                                   StudentInstitutionCourseRepository courseAssignmentRepository,
                                   CourseRepository courseRepository, InstitutionRepository institutionRepository,
                                   GranteeDetailsRepository granteeDetailsRepository,
                                   ScholarshipApplicationRepository scholarshipApplications,
                                   PaymentReminderService paymentReminderService) {
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.courseAssignmentRepository = courseAssignmentRepository;
        this.courseRepository = courseRepository;
        this.institutionRepository = institutionRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.scholarshipApplications = scholarshipApplications;
        this.paymentReminderService = paymentReminderService;
    }

    /**
     * Students sponsored on behalf of this chapter's anonymous donors (wherever the student and the sponsor are): the
     * student, the donor's label, and the sponsor who brought the donor, who is the person to contact (their name,
     * phone and chapter: as "contact_*", shown to the chapter on purpose). The donor's identity is never in Rahbar.
     */
    private List<Map<String, Object>> anonymousSponsorships(Long chapterId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.rahbar.entity.GrantorGrantee gg : grantorGranteeRepository.findAll()) {
            if (!Boolean.TRUE.equals(gg.getOnBehalf()) || !chapterId.equals(gg.getDonorChapterId())) continue;
            User st = userRepository.findById(gg.getGranteeId()).orElse(null);
            User sp = userRepository.findById(gg.getGrantorId()).orElse(null);
            if (st == null) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", st.getId());
            r.put("user_id", st.getUserId());
            r.put("name", st.getName());
            r.put("student_chapter", st.getChapterName());
            r.put("study_status", StudyStatus.label(st.getStudyStatus()));
            r.put("donor_label", gg.getDonorLabel());
            r.put("contact_name", sp == null ? null : sp.getName());
            r.put("contact_code", sp == null ? null : sp.getUserId());
            r.put("contact_phone", sp == null ? null : sp.getPhone());
            r.put("contact_chapter", sp == null ? null : sp.getChapterName());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("contact_name")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public Map<String, Object> dashboard(Long requestedChapterId) {
        return dashboard(requestedChapterId, null);
    }

    /** sessionYear: only the chapter's students of that session year ("none": without one; empty: all). */
    public Map<String, Object> dashboard(Long requestedChapterId, String sessionYear) {
        boolean scoped = Access.chapterScoped();
        Long chapterId = scoped ? Access.current().getUser().getChapterId() : requestedChapterId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scoped", scoped);
        if (!scoped) {
            body.put("chapters", Rows.pickAll(chapterRepository.findAllByOrderByChapterNameAsc(), "chapter_id", "chapter_name", "active"));
        }
        if (chapterId == null) {
            if (scoped) throw new ApiException(HttpStatus.BAD_REQUEST, "Your account has no chapter. Ask an administrator to set it.");
            body.put("chapter", null);
            return body;
        }
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chapter not found."));
        body.put("chapter", Rows.of(chapter));

        List<User> allStudents = userRepository.findByRoleIdAndChapterId(6, chapterId);
        body.put("session_years", PaymentInstallmentService.sessionYears(allStudents.stream().map(User::getId).toList(),
                allStudents.stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u, (a, b) -> a))));
        List<User> students = allStudents.stream().filter(u -> PaymentInstallmentService.ofSessionYear(u, sessionYear)).toList();
        Set<Long> studentIds = new HashSet<>();
        students.forEach(u -> studentIds.add(u.getId()));
        List<User> sponsors = userRepository.findByRoleIdAndChapterId(5, chapterId);

        // Sponsor of each student, and how many students each sponsor has.
        Map<Long, Long> sponsorOf = new HashMap<>();
        Map<Long, Long> studentCount = new HashMap<>();
        for (GrantorGrantee gg : grantorGranteeRepository.findAll()) {
            sponsorOf.put(gg.getGranteeId(), gg.getGrantorId());
            studentCount.merge(gg.getGrantorId(), 1L, Long::sum);
        }
        Map<Long, User> people = new HashMap<>();
        userRepository.findAllById(new HashSet<>(sponsorOf.values())).forEach(u -> people.put(u.getId(), u));
        Map<Long, Course> courses = new HashMap<>();
        courseRepository.findAll().forEach(c -> courses.put(c.getCourseId(), c));
        Map<String, String> institutions = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutions.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, StudentInstitutionCourse> enrolment = new HashMap<>();
        courseAssignmentRepository.findByUserIdIn(new ArrayList<>(studentIds)).forEach(s -> enrolment.put(s.getUserId(), s));

        List<Map<String, Object>> studentRows = new ArrayList<>();
        for (User u : students) {
            User sponsor = people.get(sponsorOf.get(u.getId()));
            StudentInstitutionCourse sic = enrolment.get(u.getId());
            Course course = sic == null ? null : courses.get(sic.getCourseId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("user_id", u.getUserId());
            row.put("name", u.getName());
            row.put("status", u.getStatus());
            row.put("study_status", StudyStatus.of(u.getStudyStatus()));
            row.put("sponsor_name", sponsor == null ? null : sponsor.getName());
            row.put("sponsor_code", sponsor == null ? null : sponsor.getUserId());
            row.put("institution_name", sic == null ? null : institutions.get(sic.getInstitutionId()));
            row.put("course_name", course == null ? null : course.getCourseName());
            studentRows.add(row);
        }
        studentRows.sort(Comparator.comparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> sponsorRows = new ArrayList<>();
        for (User s : sponsors) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("user_id", s.getUserId());
            row.put("name", s.getName());
            row.put("role_id", s.getRoleId());
            row.put("status", s.getStatus());
            row.put("student_count", studentCount.getOrDefault(s.getId(), 0L));
            sponsorRows.add(row);
        }
        sponsorRows.sort(Comparator.comparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> dues = paymentReminderService.dues().stream()
                .filter(d -> studentIds.contains((Long) d.get("student_id")))
                .filter(d -> "Overdue".equals(d.get("status")) || "Due soon".equals(d.get("status")))
                .toList();

        // Open scholarship applications of the chapter's students (an application has a student once accepted, so
        // these are applicants who already had an account, e.g. a sibling's or a re-application).
        List<Map<String, Object>> applications = new ArrayList<>();
        for (ScholarshipApplication a : scholarshipApplications.findByStudentUserIdIn(studentIds)) {
            if (!ApplicationService.OPEN_STATUSES.contains(a.getStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grantee_detail_id", a.getId());
            row.put("name", a.getName());
            row.put("status", ApplicationService.label(a.getStatus()));
            row.put("submitted_at", a.getCreatedAt());
            applications.add(row);
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("students", students.size());
        counts.put("studying", students.stream().filter(u -> StudyStatus.STUDYING.equals(StudyStatus.of(u.getStudyStatus()))).count());
        counts.put("withoutSponsor", studentRows.stream().filter(r -> r.get("sponsor_code") == null).count());
        counts.put("sponsors", sponsors.size());
        counts.put("paymentsOverdue", dues.stream().filter(d -> "Overdue".equals(d.get("status"))).count());
        counts.put("openApplications", applications.size());
        body.put("counts", counts);
        body.put("anonymousSponsorships", anonymousSponsorships(chapterId));
        body.put("students", studentRows);
        body.put("sponsors", sponsorRows);
        body.put("dues", dues);
        body.put("applications", applications);
        return body;
    }
}
