package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.rahbar.dto.ReportFile;
import com.rahbar.dto.ReportDefinition;

/**
 * Every report on the admin Reports page. Each report is a list of rows with a fixed column order,
 * rendered as CSV / Excel / PDF by {@link ReportService}. Reports that have a date make use of the
 * optional from / to range (inclusive days).
 */
@Service
public class ReportCatalogService {

    private static final List<ReportDefinition> CATALOG = List.of(
            new ReportDefinition("applications", "Applications", "All applications",
                    "Every application with contact numbers, latest status, interview and the sponsor of the student.", true),
            new ReportDefinition("applications_by_status", "Applications", "Applications by status",
                    "How many applications are in each status (latest status of each application).", true),
            new ReportDefinition("interviews", "Applications", "Interview schedule",
                    "Applications with a scheduled interview, soonest first.", true),

            new ReportDefinition("grantees", "Students", "Student directory",
                    "Every student with sponsor, institution, course and whether bank details are on file.", false),
            new ReportDefinition("students_without_sponsor", "Students", "Students without a sponsor",
                    "Active students who are not mapped to a sponsor (or parked on the unassigned account).", false),
            new ReportDefinition("students_without_bank", "Students", "Students without bank details",
                    "Active students who have no bank details on file yet.", false),
            new ReportDefinition("student_progress", "Students", "Progress reports",
                    "Marks uploaded by students with the review status and reviewer comments.", true),
            new ReportDefinition("alumni", "Students", "Alumni",
                    "Graduates with what they do now, organisation, city, graduation year and whether they agreed to be contacted.", false),
            new ReportDefinition("mentoring_pairs", "Students", "Mentoring pairs",
                    "Volunteers and graduates mentoring or tutoring students: who, since when, focus and status.", false),
            new ReportDefinition("volunteers", "Students", "Volunteer sign-ups",
                    "People who offered to mentor, tutor, sponsor or join a chapter through the website, with their follow-up status.", true),

            new ReportDefinition("students_by_institution", "Student breakdowns", "Students by institution",
                    "Number of students at each college: active, inactive, with and without a sponsor.", false),
            new ReportDefinition("students_by_course", "Student breakdowns", "Students by course",
                    "Number of students in each course (with its college, length and fees), including empty courses.", false),
            new ReportDefinition("students_by_institution_and_year", "Student breakdowns", "Students by institution and session year",
                    "Number of students at each college in each session year (one column per year) and in all.", false),
            new ReportDefinition("students_by_course_and_year", "Student breakdowns", "Students by course and session year",
                    "Number of students in each course in each session year (one column per year) and in all.", false),
            new ReportDefinition("students_by_chapter", "Student breakdowns", "Students by chapter",
                    "Number of students in each chapter.", false),
            new ReportDefinition("students_by_year", "Student breakdowns", "Students by academic year",
                    "Number of students in each academic year.", false),
            new ReportDefinition("students_by_study_status", "Student breakdowns", "Students by study status",
                    "Number of students studying, on hold, graduated and dropped out.", false),
            new ReportDefinition("applications_by_course", "Student breakdowns", "Applications by course applied",
                    "Demand before admission: applications per course applied for, with accepted and rejected counts.", true),
            new ReportDefinition("applications_by_rcc", "Student breakdowns", "Applications by RCC center",
                    "Applications per RCC center, with accepted and rejected counts.", true),

            new ReportDefinition("sponsors_convenors", "Sponsors", "Sponsors & convenors",
                    "Every sponsor and convenor with number of students and total paid.", false),
            new ReportDefinition("sponsors_by_chapter", "Sponsors", "Sponsors by chapter",
                    "Number of sponsors, students and total paid per chapter.", false),
            new ReportDefinition("students_by_sponsor", "Sponsors", "Students by sponsor",
                    "Each sponsor's students with institution, course, study status, since when, paid so far and overdue installments; a subtotal per sponsor, and the students without a sponsor at the end.", false),
            new ReportDefinition("sponsors_without_student", "Sponsors", "Sponsors without a student",
                    "Active sponsors with no student now: when their last student left and who it was, or that they never had one.", false),
            new ReportDefinition("sponsors_overdue", "Sponsors", "Sponsors with overdue installments",
                    "Sponsors whose students have installments unpaid after the due date: how many and the oldest due date.", false),
            new ReportDefinition("sponsorship_changes", "Sponsors", "Sponsorship changes",
                    "Students mapped to a sponsor, or moved off one, in the period (from the mapping history).", true),

            new ReportDefinition("payments", "Payments", "All payments",
                    "Every payment with student, sponsor, installment, status and date.", true),
            new ReportDefinition("payments_by_month", "Payments", "Payments by month",
                    "Number of installments paid per month.", true),
            new ReportDefinition("payments_by_sponsor", "Payments", "Payments by sponsor",
                    "Number of installments paid per sponsor.", true),
            new ReportDefinition("pending_payments", "Payments", "Payments awaiting approval",
                    "Payments recorded with a pending status.", true),
            new ReportDefinition("payment_dues", "Payments", "Payment dues",
                    "Installments due vs paid for every sponsored student, with overdue counts and next due date.", false),
            new ReportDefinition("online_payments_80g", "Payments", "Online payments & donations (80G / Form 10BD)",
                    "Every online payment received through Razorpay in the period: 80G receipt number, date, payer, PAN, address, amount and what it was for. The list your CA needs for Form 10BD (choose the financial year: 1 April to 31 March).", true),
            new ReportDefinition("payment_schedules", "Payments", "Payment amount by year",
                    "The configured scholarship amount for each academic year.", false),

            new ReportDefinition("institutions_courses", "Setup", "Institutions & courses",
                    "Every course with its institution, fees, length and number of students enrolled.", false),
            new ReportDefinition("rcc_centers", "Setup", "RCC centers", "All RCC centers with incharge and contact.", false),
            new ReportDefinition("users_by_role", "Setup", "Users by role",
                    "Number of active and inactive users in each role.", false),

            new ReportDefinition("activity_log", "System", "Activity log",
                    "Who did what and when (last 30 days unless a date range is chosen).", true)
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final InstitutionRepository institutionRepository;
    private final CourseRepository courseRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final RccCenterRepository rccCenterRepository;
    private final PaymentReminderService paymentReminderService;
    private final com.rahbar.security.SponsorPrivacy sponsorPrivacy;
    private final ActivityLogService activityLogService;
    private final ReportService reportService;
    private final com.rahbar.security.StudentScope studentScope;
    private final PaymentInstallmentRepository installmentRepository;
    private final SponsorMappingHistoryRepository mappingHistoryRepository;
    private final VolunteerApplicationRepository volunteerRepository;
    private final ChapterRepository chapterRepository;
    private final AlumniService alumniService;
    private final MentoringService mentoringService;

    public ReportCatalogService(com.rahbar.security.SponsorPrivacy sponsorPrivacy, UserRepository userRepository, RoleRepository roleRepository,
                                GranteeDetailsRepository granteeDetailsRepository,
                                ApplicationStatusRepository applicationStatusRepository,
                                GrantorGranteeRepository grantorGranteeRepository,
                                StudentInstitutionCourseRepository studentCourseRepository,
                                InstitutionRepository institutionRepository, CourseRepository courseRepository,
                                BankDetailsRepository bankDetailsRepository, PaymentRepository paymentRepository,
                                PaymentScheduleRepository paymentScheduleRepository,
                                StudentProgressRepository studentProgressRepository,
                                RccCenterRepository rccCenterRepository, PaymentReminderService paymentReminderService,
                                ActivityLogService activityLogService, ReportService reportService,
                                com.rahbar.security.StudentScope studentScope, PaymentInstallmentRepository installmentRepository,
                                SponsorMappingHistoryRepository mappingHistoryRepository, VolunteerApplicationRepository volunteerRepository,
                                ChapterRepository chapterRepository, AlumniService alumniService, MentoringService mentoringService) {
        this.studentScope = studentScope;
        this.installmentRepository = installmentRepository;
        this.mappingHistoryRepository = mappingHistoryRepository;
        this.volunteerRepository = volunteerRepository;
        this.chapterRepository = chapterRepository;
        this.alumniService = alumniService;
        this.mentoringService = mentoringService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.institutionRepository = institutionRepository;
        this.courseRepository = courseRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.paymentRepository = paymentRepository;
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.paymentReminderService = paymentReminderService;
        this.sponsorPrivacy = sponsorPrivacy;
        this.activityLogService = activityLogService;
        this.reportService = reportService;
    }

    /** The reports the signed-in user may open: chapter / RCC-scoped users get the ones filtered to their students. */
    public List<ReportDefinition> catalog() {
        boolean all = studentScope.current().all();
        return CATALOG.stream().filter(d -> all || !ORGANISATION_WIDE.contains(d.key())).filter(d -> allowed(d.key())).toList();
    }

    /** Reports about the whole organisation (not filtered per chapter / RCC center): not for scoped users. */
    private static final Set<String> ORGANISATION_WIDE = Set.of("sponsors_by_chapter", "users_by_role", "activity_log", "volunteers", "online_payments_80g");

    /** Reports with people's contact details that also need VIEW on the screen they come from. */
    private static final Map<String, com.rahbar.security.Section> NEEDS_SECTION = Map.of(
            "alumni", com.rahbar.security.Section.ALUMNI,
            "mentoring_pairs", com.rahbar.security.Section.MENTORING,
            "volunteers", com.rahbar.security.Section.VOLUNTEERS,
            "online_payments_80g", com.rahbar.security.Section.ONLINE_PAYMENTS);

    private static boolean allowed(String key) {
        com.rahbar.security.Section section = NEEDS_SECTION.get(key);
        return section == null || com.rahbar.security.Access.can(section, com.rahbar.security.Section.Level.VIEW);
    }

    // ---------------------------------------------------------------- what the signed-in user may see

    /** The scope of the report being built (set in rows(); unscoped users see everything). */
    private static final ThreadLocal<com.rahbar.security.StudentScope.Scope> SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Map<Long, User>> USERS = new ThreadLocal<>();

    private com.rahbar.security.StudentScope.Scope scope() {
        com.rahbar.security.StudentScope.Scope s = SCOPE.get();
        return s == null ? com.rahbar.security.StudentScope.Scope.ALL : s;
    }

    private Map<Long, User> scopeUsers() {
        Map<Long, User> users = USERS.get();
        if (users == null) {
            users = new HashMap<>();
            for (User u : userRepository.findAll()) users.put(u.getId(), u);
            USERS.set(users);
        }
        return users;
    }

    private boolean seesStudent(Long studentId) {
        return scope().all() || scope().includesId(studentId, scopeUsers());
    }

    private List<User> visibleStudents() {
        List<User> all = userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE);
        return scope().all() ? all : all.stream().filter(scope()::includes).toList();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ScholarshipApplicationRepository scholarshipApplications;

    /** Scholarship applications the user may see. */
    private List<ScholarshipApplication> visibleApplications() {
        List<ScholarshipApplication> all = scholarshipApplications.findAll();
        return scope().all() ? all : all.stream().filter(g -> scope().includesApplication(g, scopeUsers())).toList();
    }

    /** Payments to students the user may see. */
    private List<Payment> visiblePayments() {
        List<Payment> all = paymentRepository.findAll();
        return scope().all() ? all : all.stream().filter(p -> seesStudent(p.getGranteeId())).toList();
    }

    /** Builds a report download. from / to are optional inclusive days (ignored by reports without a date). */
    public ReportFile build(String key, String format, LocalDate from, LocalDate to) {
        ReportDefinition def = definition(key);
        String name = key + "_report" + (def.dateRange() && (from != null || to != null)
                ? "_" + (from == null ? "start" : from) + "_to_" + (to == null ? "today" : to) : "");
        return reportService.build(privateRows(key, from, to), name, format, def.title());
    }

    /** The report's rows for viewing on screen: { title, columns, rows } (an empty report has no columns). */
    public Map<String, Object> data(String key, LocalDate from, LocalDate to) {
        ReportDefinition def = definition(key);
        List<Map<String, Object>> rows = privateRows(key, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("title", def.title());
        body.put("columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
        body.put("rows", rows);
        return body;
    }

    private ReportDefinition definition(String key) {
        ReportDefinition def = CATALOG.stream().filter(d -> d.key().equals(key)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type selected."));
        if (ORGANISATION_WIDE.contains(key) && !studentScope.current().all()) {
            throw com.rahbar.security.Access.forbidden("This report covers the whole organisation and isn't available for your role.");
        }
        if (!allowed(key)) throw com.rahbar.security.Access.forbidden("This report needs access to " + NEEDS_SECTION.get(key).label() + ".");
        return def;
    }

    /** Report rows with sponsors' details blanked unless the user may see them (see SponsorPrivacy). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> privateRows(String key, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = rows(key, from, to);
        if (!sponsorPrivacy.hidesDetails()) return rows;
        List<Map<String, Object>> masked = (List<Map<String, Object>>) sponsorPrivacy.mask(rows);
        masked.forEach(r -> r.remove("masked")); // marker for screens, not a report column
        return masked;
    }

    /** Rows of the report; from / to are optional inclusive days (ignored by reports without a date). */
    private List<Map<String, Object>> rows(String key, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The 'to' date cannot be before the 'from' date.");
        }
        DateRange range = new DateRange(from, to);
        SCOPE.set(studentScope.current());
        try {
            return switch (key) {
                case "applications" -> applications(range);
                case "applications_by_status" -> applicationsByStatus(range);
                case "interviews" -> interviews(range);
                case "grantees" -> students(false, false);
                case "students_without_sponsor" -> students(true, false);
                case "students_without_bank" -> students(false, true);
                case "student_progress" -> studentProgress(range);
                case "sponsors_convenors" -> sponsors();
                case "sponsors_by_chapter" -> sponsorsByChapter();
                case "students_by_sponsor" -> studentsBySponsor();
                case "sponsors_without_student" -> sponsorsWithoutStudent();
                case "sponsors_overdue" -> sponsorsOverdue();
                case "sponsorship_changes" -> sponsorshipChanges(range);
                case "alumni" -> alumni();
                case "mentoring_pairs" -> mentoringPairs();
                case "volunteers" -> volunteers(range);
                case "online_payments_80g" -> onlinePayments80g(range);
                case "payments" -> payments(range, null);
                case "pending_payments" -> payments(range, "pending");
                case "payments_by_month" -> paymentsByMonth(range);
                case "payments_by_sponsor" -> paymentsBySponsor(range);
                case "payment_dues" -> paymentReminderService.dues().stream()
                        .filter(d -> seesStudent(((Number) d.get("student_id")).longValue())).toList();
                case "payment_schedules" -> paymentSchedules();
                case "institutions_courses" -> institutionsCourses();
                case "students_by_institution" -> studentsByInstitution();
                case "students_by_course" -> studentsByCourse();
                case "students_by_institution_and_year" -> studentsByYearTable(true);
                case "students_by_course_and_year" -> studentsByYearTable(false);
                case "students_by_chapter" -> studentsBy(u -> ServiceSupport.chapterLabel(u.getChapterName()), "chapter");
                case "students_by_study_status" -> studentsBy(u -> StudyStatus.label(u.getStudyStatus()), "study_status");
                case "students_by_year" -> studentsBy(u -> u.getYear() == null ? "Not set" : String.valueOf(u.getYear()), "academic_year");
                case "applications_by_course" -> applicationsBy(ScholarshipApplication::getCourseApplied, "course_applied", range);
                case "applications_by_rcc" -> applicationsBy(ScholarshipApplication::getRccName, "rcc_center", range);
                case "rcc_centers" -> rccCenters();
                case "users_by_role" -> usersByRole();
                case "activity_log" -> activityLog(from, to);
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type selected.");
            };
        } finally {
            SCOPE.remove();
            USERS.remove();
        }
    }

    // ---------------------------------------------------------------- applications

    private List<Map<String, Object>> applications(DateRange range) {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScholarshipApplication g : sorted(visibleApplications(), ScholarshipApplication::getId)) {
            if (!range.contains(g.getCreatedAt())) continue;
            Long sponsorId = g.getStudentUserId() == null ? null : sponsorOf.get(g.getStudentUserId());
            rows.add(row(
                    "application_id", g.getId(),
                    "student_name", g.getName(),
                    "email", g.getEmail(),
                    "student_user_id", codes.get(g.getStudentUserId()),
                    "father_name", g.getFatherName(),
                    "mother_name", g.getMotherName(),
                    "student_mobile", g.getStudentMobile(),
                    "father_mobile", g.getFatherMobile(),
                    "mother_mobile", g.getMotherMobile(),
                    "address", g.getAddress(),
                    "rcc_name", g.getRccName(),
                    "course_applied", g.getCourseApplied(),
                    "annual_income", g.getAverageAnnualSalary(),
                    "submitted_at", g.getCreatedAt(),
                    "status", ApplicationService.label(g.getStatus()),
                    "decided_at", g.getDecidedAt(),
                    "interview_at", g.getInterviewAt(),
                    "interview_venue", g.getInterviewVenue(),
                    "assigned_sponsor_id", codes.get(sponsorId),
                    "assigned_sponsor_name", sponsorId == null ? null : names.get(sponsorId)));
        }
        return rows;
    }

    private List<Map<String, Object>> applicationsByStatus(DateRange range) {
        Map<String, Long> counts = new TreeMap<>();
        for (ScholarshipApplication g : visibleApplications()) {
            if (!range.contains(g.getCreatedAt())) continue;
            counts.merge(ApplicationService.label(g.getStatus()), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((status, count) -> rows.add(row("status", status, "applications", count)));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("applications")).reversed());
        return rows;
    }

    private List<Map<String, Object>> interviews(DateRange range) {
        List<Map<String, Object>> rows = new ArrayList<>();
        visibleApplications().stream()
                .filter(g -> g.getInterviewAt() != null && range.contains(g.getInterviewAt()))
                .sorted(Comparator.comparing(ScholarshipApplication::getInterviewAt))
                .forEach(g -> rows.add(row(
                        "interview_at", g.getInterviewAt(),
                        "venue", g.getInterviewVenue(),
                        "application_id", g.getId(),
                        "student_name", g.getName(),
                        "student_mobile", g.getStudentMobile(),
                        "father_mobile", g.getFatherMobile(),
                        "course_applied", g.getCourseApplied(),
                        "rcc_name", g.getRccName())));
        return rows;
    }

    // ---------------------------------------------------------------- students

    private List<Map<String, Object>> students(boolean withoutSponsorOnly, boolean withoutBankOnly) {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        Set<Long> withBank = bankDetailsRepository.findAll().stream().map(BankDetails::getUserId).collect(Collectors.toSet());
        Map<String, String> institutionNames = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutionNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, Course> courses = new HashMap<>();
        courseRepository.findAll().forEach(c -> courses.put(c.getCourseId(), c));
        Map<Long, StudentInstitutionCourse> enrolment = enrolments();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : sorted(visibleStudents(), User::getUserId)) {
            boolean active = !"Inactive".equalsIgnoreCase(u.getStatus());
            Long sponsorId = sponsorOf.get(u.getId());
            boolean hasSponsor = sponsorId != null && !sponsorId.equals(unassigned);
            boolean hasBank = withBank.contains(u.getId());
            if (withoutSponsorOnly && (!active || hasSponsor)) continue;
            if (withoutBankOnly && (!active || hasBank)) continue;
            StudentInstitutionCourse sic = enrolment.get(u.getId());
            Course course = sic == null ? null : courses.get(sic.getCourseId());
            rows.add(row(
                    "student_id", u.getUserId(),
                    "name", u.getName(),
                    "email", u.getEmail(),
                    "phone", u.getPhone(),
                    "chapter", u.getChapterName(),
                    "year", u.getYear(),
                    "status", u.getStatus(),
                    "study_status", StudyStatus.label(u.getStudyStatus()),
                    "sponsor_id", hasSponsor ? codes.get(sponsorId) : null,
                    "sponsor_name", hasSponsor ? names.get(sponsorId) : null,
                    "institution", sic == null ? null : institutionNames.get(sic.getInstitutionId()),
                    "course", course == null ? null : course.getCourseName(),
                    "course_start", sic == null ? null : sic.getAssignedAt(),
                    "bank_details", hasBank ? "Yes" : "No",
                    "joined", u.getCreatedAt()));
        }
        return rows;
    }

    private List<Map<String, Object>> studentProgress(DateRange range) {
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        List<Map<String, Object>> rows = new ArrayList<>();
        studentProgressRepository.findAll().stream()
                .filter(p -> range.contains(p.getCreatedAt()))
                .filter(p -> seesStudent(p.getGranteeId()))
                .sorted(Comparator.comparing(StudentProgress::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(p -> {
                    Long sponsorId = sponsorOf.get(p.getGranteeId());
                    rows.add(row(
                            "student_id", codes.get(p.getGranteeId()),
                            "student_name", names.get(p.getGranteeId()),
                            "sponsor_name", sponsorId == null ? null : names.get(sponsorId),
                            "year", p.getYear(),
                            "session", p.getSession(),
                            "marks", p.getMarks(),
                            "uploaded_at", p.getCreatedAt(),
                            "review_status", p.getReviewStatus() == null ? "Pending" : p.getReviewStatus(),
                            "review_comment", p.getReviewComment(),
                            "reviewed_by", p.getReviewedBy() == null ? null : names.get(p.getReviewedBy()),
                            "reviewed_at", p.getReviewedAt(),
                            "file", p.getFilePath()));
                });
        return rows;
    }

    // ---------------------------------------------------------------- student breakdowns

    /** Running counts for one group of students. */
    private static final class Tally {
        long total, active, inactive, sponsored;

        void add(User u, boolean hasSponsor) {
            total++;
            if ("Inactive".equalsIgnoreCase(u.getStatus())) inactive++; else active++;
            if (hasSponsor) sponsored++;
        }

        void into(Map<String, Object> row) {
            row.put("total_students", total);
            row.put("active", active);
            row.put("inactive", inactive);
            row.put("with_sponsor", sponsored);
            row.put("without_sponsor", total - sponsored);
        }
    }

    private static boolean hasSponsor(Map<Long, Long> sponsorOf, Long studentId, Long unassigned) {
        Long sponsorId = sponsorOf.get(studentId);
        return sponsorId != null && !sponsorId.equals(unassigned);
    }

    private Map<Long, StudentInstitutionCourse> enrolments() {
        Map<Long, StudentInstitutionCourse> map = new HashMap<>();
        studentCourseRepository.findAll().forEach(s -> map.put(s.getUserId(), s));
        return map;
    }

    private List<Map<String, Object>> studentsByInstitution() {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Long unassignedGrantor = ServiceSupport.unassignedGrantorId(userRepository);
        Map<Long, StudentInstitutionCourse> enrolment = enrolments();
        Map<String, Long> coursesOffered = courseRepository.findAll().stream()
                .collect(Collectors.groupingBy(Course::getInstitutionId, Collectors.counting()));
        Map<String, Tally> tallies = new HashMap<>();
        Tally unassigned = new Tally();
        for (User u : visibleStudents()) {
            StudentInstitutionCourse sic = enrolment.get(u.getId());
            Tally t = sic == null ? unassigned : tallies.computeIfAbsent(sic.getInstitutionId(), k -> new Tally());
            t.add(u, hasSponsor(sponsorOf, u.getId(), unassignedGrantor));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Institution i : institutionRepository.findAll()) {
            Tally t = tallies.getOrDefault(i.getInstitutionId(), new Tally());
            Map<String, Object> row = row("institution_id", i.getInstitutionId(), "institution", i.getInstitutionName(),
                    "courses_offered", coursesOffered.getOrDefault(i.getInstitutionId(), 0L));
            t.into(row);
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("total_students")).reversed()
                .thenComparing(r -> String.valueOf(r.get("institution")), String.CASE_INSENSITIVE_ORDER));
        if (unassigned.total > 0) {
            Map<String, Object> row = row("institution_id", null, "institution", "No course assigned yet", "courses_offered", null);
            unassigned.into(row);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> studentsByCourse() {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Long unassignedGrantor = ServiceSupport.unassignedGrantorId(userRepository);
        Map<Long, StudentInstitutionCourse> enrolment = enrolments();
        Map<String, String> institutionNames = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutionNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, Tally> tallies = new HashMap<>();
        Tally unassigned = new Tally();
        for (User u : visibleStudents()) {
            StudentInstitutionCourse sic = enrolment.get(u.getId());
            Tally t = sic == null ? unassigned : tallies.computeIfAbsent(sic.getCourseId(), k -> new Tally());
            t.add(u, hasSponsor(sponsorOf, u.getId(), unassignedGrantor));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Course c : courseRepository.findAll()) {
            Tally t = tallies.getOrDefault(c.getCourseId(), new Tally());
            Map<String, Object> row = row("institution", institutionNames.get(c.getInstitutionId()), "course_id", c.getCourseId(),
                    "course", c.getCourseName(), "semesters", c.getNumberOfSemesters(), "fees_per_semester", c.getFeesPerSemester());
            t.into(row);
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("total_students")).reversed()
                .thenComparing(r -> String.valueOf(r.get("institution")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> String.valueOf(r.get("course")), String.CASE_INSENSITIVE_ORDER));
        if (unassigned.total > 0) {
            Map<String, Object> row = row("institution", null, "course_id", null, "course", "No course assigned yet",
                    "semesters", null, "fees_per_semester", null);
            unassigned.into(row);
            rows.add(row);
        }
        return rows;
    }

    /**
     * Students per institution (or per course, with its institution) and session year: one row each, a column per
     * session year (oldest first, then "year_not_set"), and the total. Every student the user may see, whatever their
     * study status; a student without a course counts under "No course assigned yet".
     */
    private List<Map<String, Object>> studentsByYearTable(boolean byInstitution) {
        Map<Long, StudentInstitutionCourse> enrolment = enrolments();
        Map<String, String> institutionNames = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutionNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, Course> courses = new HashMap<>();
        courseRepository.findAll().forEach(c -> courses.put(c.getCourseId(), c));
        TreeSet<Integer> years = new TreeSet<>();
        // group key -> (year or null -> students)
        Map<String, Map<Integer, Long>> counts = new LinkedHashMap<>();
        Map<String, String[]> labels = new HashMap<>(); // key -> [institution, course]
        for (User u : visibleStudents()) {
            if (u.getMergedIntoId() != null) continue;
            StudentInstitutionCourse sic = enrolment.get(u.getId());
            String key;
            String[] label;
            if (sic == null) { key = "~none"; label = new String[]{"No course assigned yet", null}; }
            else if (byInstitution) { key = "i:" + sic.getInstitutionId(); label = new String[]{institutionNames.get(sic.getInstitutionId()), null}; }
            else {
                Course c = courses.get(sic.getCourseId());
                key = "c:" + sic.getCourseId();
                label = new String[]{institutionNames.get(sic.getInstitutionId()), c == null ? null : c.getCourseName()};
            }
            labels.putIfAbsent(key, label);
            if (u.getYear() != null) years.add(u.getYear());
            counts.computeIfAbsent(key, k -> new HashMap<>()).merge(u.getYear(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Long>> e : counts.entrySet()) {
            String[] l = labels.get(e.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("institution", l[0]);
            if (!byInstitution) row.put("course", l[1]);
            long total = 0;
            for (Integer y : years) {
                long n = e.getValue().getOrDefault(y, 0L);
                row.put("year_" + y, n);
                total += n;
            }
            long none = e.getValue().getOrDefault(null, 0L);
            row.put("year_not_set", none);
            row.put("total_students", total + none);
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> "No course assigned yet".equals(r.get("institution")) ? 1 : 0)
                .thenComparing(r -> -(Long) r.get("total_students"))
                .thenComparing(r -> String.valueOf(r.get("institution")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Students grouped by any label (chapter, academic year...), biggest group first. */
    private List<Map<String, Object>> studentsBy(Function<User, String> label, String column) {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Long unassignedGrantor = ServiceSupport.unassignedGrantorId(userRepository);
        Map<String, Tally> tallies = new HashMap<>();
        for (User u : visibleStudents()) {
            tallies.computeIfAbsent(label.apply(u), k -> new Tally()).add(u, hasSponsor(sponsorOf, u.getId(), unassignedGrantor));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        tallies.forEach((key, t) -> {
            Map<String, Object> row = row(column, key);
            t.into(row);
            rows.add(row);
        });
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("total_students")).reversed()
                .thenComparing(r -> String.valueOf(r.get(column)), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Applications grouped by a free-text field of the form (course applied, RCC), with outcome counts. */
    private List<Map<String, Object>> applicationsBy(Function<ScholarshipApplication, String> field, String column, DateRange range) {
        Map<String, long[]> counts = new HashMap<>();   // [applications, accepted, rejected]
        Map<String, String> display = new HashMap<>();  // first spelling seen for each case-insensitive key
        for (ScholarshipApplication g : visibleApplications()) {
            if (!range.contains(g.getCreatedAt())) continue;
            String value = field.apply(g);
            String shown = value == null || value.isBlank() ? "Not given" : value.trim().replaceAll("\\s+", " ");
            String key = shown.toLowerCase(Locale.ROOT);
            display.putIfAbsent(key, shown);
            long[] c = counts.computeIfAbsent(key, k -> new long[3]);
            c[0]++;
            if (ScholarshipApplication.ACCEPTED.equals(g.getStatus())) c[1]++;
            if (ScholarshipApplication.REJECTED.equals(g.getStatus())) c[2]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((key, c) -> rows.add(row(column, display.get(key), "applications", c[0],
                "accepted", c[1], "rejected", c[2], "in_progress", c[0] - c[1] - c[2])));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("applications")).reversed()
                .thenComparing(r -> String.valueOf(r.get(column)), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    // ---------------------------------------------------------------- sponsors

    private List<Map<String, Object>> sponsors() {
        Map<Long, Long> studentCount = grantorGranteeRepository.findAll().stream()
                .collect(Collectors.groupingBy(GrantorGrantee::getGrantorId, Collectors.counting()));
        Map<Long, Long> paid = paidCountsByGrantor(DateRange.ALL);
        Map<Integer, String> roleNames = roleNames();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : sorted(userRepository.findByRoleIdIn(ServiceSupport.SPONSOR_CONVENOR_ROLES), User::getName)) {
            if (!scope().includesSponsor(u)) continue;
            rows.add(row(
                    "user_id", u.getUserId(),
                    "name", u.getName(),
                    "role", roleNames.get(u.getRoleId()),
                    "email", u.getEmail(),
                    "phone", u.getPhone(),
                    "chapter", u.getChapterName(),
                    "status", u.getStatus(),
                    "students", studentCount.getOrDefault(u.getId(), 0L),
                    "installments_paid", paid.getOrDefault(u.getId(), 0L),
                    "joined", u.getCreatedAt()));
        }
        return rows;
    }

    private List<Map<String, Object>> sponsorsByChapter() {
        Map<Long, Long> studentCount = grantorGranteeRepository.findAll().stream()
                .collect(Collectors.groupingBy(GrantorGrantee::getGrantorId, Collectors.counting()));
        Map<Long, Long> paid = paidCountsByGrantor(DateRange.ALL);
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, Long> totals = new TreeMap<>();
        for (User u : userRepository.findByRoleId(5)) {
            String chapter = ServiceSupport.chapterLabel(u.getChapterName());
            long[] c = counts.computeIfAbsent(chapter, r -> new long[2]);
            c[0]++;
            c[1] += studentCount.getOrDefault(u.getId(), 0L);
            totals.merge(chapter, paid.getOrDefault(u.getId(), 0L), Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((chapter, c) -> rows.add(row("chapter", chapter, "sponsors", c[0], "students", c[1],
                "installments_paid", totals.getOrDefault(chapter, 0L))));
        return rows;
    }

    // ---------------------------------------------------------------- sponsors and their students

    /**
     * Each sponsor (by name) with their students, a subtotal row per sponsor, then the students without a sponsor
     * ("Unassigned"). Paid = installments this sponsor paid for the student; overdue = installments unpaid after the due date.
     */
    private List<Map<String, Object>> studentsBySponsor() {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
        Map<Long, User> users = scopeUsers();
        Map<String, String> institutionNames = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutionNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, Course> courses = new HashMap<>();
        courseRepository.findAll().forEach(c -> courses.put(c.getCourseId(), c));
        Map<Long, StudentInstitutionCourse> enrolment = enrolments();
        Map<String, java.time.LocalDateTime> since = new HashMap<>(); // "sponsor:student" -> start of the current mapping
        for (SponsorMappingHistory h : mappingHistoryRepository.findAll()) {
            if (h.getEndedAt() == null && h.getStartedAt() != null) since.merge(h.getGrantorId() + ":" + h.getGranteeId(), h.getStartedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        Map<String, Long> paid = new HashMap<>();
        for (Payment p : visiblePayments()) {
            if (!"Paid".equalsIgnoreCase(p.getStatus()) || p.getGrantorId() == null) continue;
            paid.merge(p.getGrantorId() + ":" + p.getGranteeId(), 1L, Long::sum);
        }
        Map<String, long[]> overdueCount = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (PaymentInstallment i : installmentRepository.findAllByOrderByGranteeIdAscInstallmentNoAsc()) {
            if (i.getPaymentId() != null || i.getDueDate() == null || !i.getDueDate().isBefore(today)) continue;
            String k = i.getGrantorId() + ":" + i.getGranteeId();
            overdueCount.computeIfAbsent(k, x -> new long[1])[0]++;
        }

        // sponsor users.id (null = unassigned) -> their students, sponsors by name, unassigned last
        Map<Long, List<User>> bySponsor = new HashMap<>();
        for (User u : visibleStudents()) {
            if (u.getMergedIntoId() != null) continue;
            Long sponsorId = sponsorOf.get(u.getId());
            if (sponsorId != null && sponsorId.equals(unassigned)) sponsorId = null;
            bySponsor.computeIfAbsent(sponsorId, k -> new ArrayList<>()).add(u);
        }
        List<Long> order = new ArrayList<>(bySponsor.keySet());
        order.sort(Comparator.comparing((Long id) -> id == null ? 1 : 0)
                .thenComparing(id -> id == null || users.get(id) == null ? "" : String.valueOf(users.get(id).getName()), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long sponsorId : order) {
            User sp = sponsorId == null ? null : users.get(sponsorId);
            String code = sponsorId == null ? null : sp == null ? null : sp.getUserId();
            String name = sponsorId == null ? "Unassigned" : sp == null ? "(unknown sponsor)" : sp.getName();
            String chapter = sp == null ? null : sp.getChapterName();
            long paidSum = 0, overdueN = 0;
            List<User> students = sorted(bySponsor.get(sponsorId), User::getName);
            for (User u : students) {
                String k = sponsorId + ":" + u.getId();
                StudentInstitutionCourse sic = enrolment.get(u.getId());
                Course course = sic == null ? null : courses.get(sic.getCourseId());
                long p = sponsorId == null ? 0 : paid.getOrDefault(k, 0L);
                long on = sponsorId == null ? 0 : overdueCount.getOrDefault(k, new long[1])[0];
                paidSum += p;
                overdueN += on;
                java.time.LocalDateTime from = sponsorId == null ? null : since.get(k);
                rows.add(row("sponsor_id", code, "sponsor_name", name, "sponsor_chapter", chapter,
                        "student_id", u.getUserId(), "student_name", u.getName(), "student_chapter", u.getChapterName(),
                        "institution", sic == null ? null : institutionNames.get(sic.getInstitutionId()),
                        "course", course == null ? null : course.getCourseName(),
                        "study_status", StudyStatus.label(u.getStudyStatus()), "status", u.getStatus(),
                        "sponsored_since", from == null ? null : from.toLocalDate(),
                        "installments_paid", p, "overdue_installments", on));
            }
            rows.add(row("sponsor_id", code, "sponsor_name", name, "sponsor_chapter", chapter,
                    "student_id", null, "student_name", "Subtotal: " + students.size() + " student" + (students.size() == 1 ? "" : "s"),
                    "student_chapter", null, "institution", null, "course", null, "study_status", null, "status", null,
                    "sponsored_since", null, "installments_paid", paidSum, "overdue_installments", overdueN));
        }
        return rows;
    }

    /** Active sponsors (the user may see) with no student now; most recently freed first, never-sponsored last. */
    private List<Map<String, Object>> sponsorsWithoutStudent() {
        Set<Long> withStudent = new HashSet<>();
        grantorGranteeRepository.findAll().forEach(m -> { if (m.getGrantorId() != null) withStudent.add(m.getGrantorId()); });
        Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
        Map<Long, SponsorMappingHistory> lastEnded = new HashMap<>();
        for (SponsorMappingHistory h : mappingHistoryRepository.findAll()) {
            if (h.getEndedAt() == null) continue;
            lastEnded.merge(h.getGrantorId(), h, (a, b) -> a.getEndedAt().isAfter(b.getEndedAt()) ? a : b);
        }
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User s : userRepository.findByRoleIdIn(ServiceSupport.SPONSOR_ROLES)) {
            if ("Inactive".equalsIgnoreCase(s.getStatus()) || s.getId().equals(unassigned) || s.getMergedIntoId() != null) continue;
            if (withStudent.contains(s.getId()) || !scope().includesSponsor(s)) continue;
            SponsorMappingHistory h = lastEnded.get(s.getId());
            rows.add(row("sponsor_id", s.getUserId(), "sponsor_name", s.getName(), "sponsor_chapter", s.getChapterName(),
                    "without_student_since", h == null ? null : h.getEndedAt().toLocalDate(),
                    "last_student_id", h == null ? null : codes.get(h.getGranteeId()),
                    "last_student_name", h == null ? "Never had a student" : names.get(h.getGranteeId())));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDate) r.get("without_student_since"), Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /** Sponsors with installments unpaid after the due date (students the user may see), oldest due first. */
    private List<Map<String, Object>> sponsorsOverdue() {
        LocalDate today = LocalDate.now();
        Map<Long, User> users = scopeUsers();
        Map<Long, Object[]> bySponsor = new LinkedHashMap<>(); // students, count, oldest
        for (PaymentInstallment i : installmentRepository.findAllByOrderByGranteeIdAscInstallmentNoAsc()) {
            if (i.getPaymentId() != null || i.getDueDate() == null || !i.getDueDate().isBefore(today) || i.getGrantorId() == null) continue;
            if (!seesStudent(i.getGranteeId())) continue;
            Object[] a = bySponsor.computeIfAbsent(i.getGrantorId(), k -> new Object[]{new HashSet<Long>(), 0L, i.getDueDate()});
            @SuppressWarnings("unchecked") Set<Long> st = (Set<Long>) a[0];
            st.add(i.getGranteeId());
            a[1] = (Long) a[1] + 1;
            if (i.getDueDate().isBefore((LocalDate) a[2])) a[2] = i.getDueDate();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        bySponsor.forEach((id, a) -> {
            User s = users.get(id);
            rows.add(row("sponsor_id", s == null ? null : s.getUserId(), "sponsor_name", s == null ? null : s.getName(),
                    "sponsor_chapter", s == null ? null : s.getChapterName(), "students", ((Set<?>) a[0]).size(),
                    "overdue_installments", a[1], "oldest_due", a[2],
                    "days_overdue", java.time.temporal.ChronoUnit.DAYS.between((LocalDate) a[2], today)));
        });
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDate) r.get("oldest_due")));
        return rows;
    }

    /** Mapping history in the period: a "Mapped" row when a student got a sponsor, an "Ended" row when it stopped. */
    private List<Map<String, Object>> sponsorshipChanges(DateRange range) {
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SponsorMappingHistory h : mappingHistoryRepository.findAll()) {
            if (!seesStudent(h.getGranteeId())) continue;
            if (h.getStartedAt() != null && range.contains(h.getStartedAt())) rows.add(change(h.getStartedAt(), "Mapped", h, names, codes));
            if (h.getEndedAt() != null && range.contains(h.getEndedAt())) rows.add(change(h.getEndedAt(), "Ended", h, names, codes));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (java.time.LocalDateTime) r.get("date")).reversed());
        return rows;
    }

    private static Map<String, Object> change(java.time.LocalDateTime when, String what, SponsorMappingHistory h,
                                              Map<Long, String> names, Map<Long, String> codes) {
        return row("date", when, "change", what, "student_id", codes.get(h.getGranteeId()), "student_name", names.get(h.getGranteeId()),
                "sponsor_id", codes.get(h.getGrantorId()), "sponsor_name", names.get(h.getGrantorId()));
    }

    // ---------------------------------------------------------------- alumni, mentoring, volunteers

    /** Graduates (the user may see) and what they do now (Admin > Alumni). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> alumni() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> a : (List<Map<String, Object>>) alumniService.list().get("rows")) {
            rows.add(row("student_id", a.get("user_id"), "name", a.get("name"), "chapter", a.get("chapter"),
                    "course", a.get("course"), "institution", a.get("institution"), "graduation_year", a.get("graduation_year"),
                    "now", a.get("current_status_label"), "organisation", a.get("organisation"), "role", a.get("role_title"),
                    "city", a.get("city"), "country", a.get("country"),
                    "agreed_to_be_contacted", Boolean.TRUE.equals(a.get("consent_to_contact")) ? "Yes" : "No",
                    "email", a.get("email"), "phone", a.get("phone"), "linkedin", a.get("linkedin_url")));
        }
        return rows;
    }

    /** Mentoring pairs (students the user may see), active first (Students > Mentoring). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mentoringPairs() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> m : (List<Map<String, Object>>) mentoringService.overview().get("rows")) {
            rows.add(row("student_id", m.get("student_ref_id"), "student_name", m.get("student_name"), "chapter", m.get("chapter"),
                    "mentor", m.get("mentor_name"), "mentor_is", "ALUMNI".equals(m.get("mentor_kind")) ? "Graduate" : "Volunteer",
                    "mentor_email", m.get("mentor_email"), "mentor_phone", m.get("mentor_phone"),
                    "help", m.get("help_label"), "focus", m.get("focus"), "status", "ACTIVE".equals(m.get("status")) ? "Active" : "Ended",
                    "started_on", m.get("started_on"), "ended_on", m.get("ended_on"), "notes", m.get("notes")));
        }
        return rows;
    }

    /** Website volunteer sign-ups in the period (by sign-up date), newest first. */
    private List<Map<String, Object>> volunteers(DateRange range) {
        Map<Long, String> chapters = new HashMap<>();
        chapterRepository.findAll().forEach(c -> chapters.put(c.getChapterId(), c.getChapterName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (VolunteerApplication v : volunteerRepository.findAllByOrderByCreatedAtDesc()) {
            if (!range.contains(v.getCreatedAt())) continue;
            List<String> offers = new ArrayList<>();
            if (v.isWantsMentor()) offers.add("Mentor");
            if (v.isWantsTutor()) offers.add("Tutor");
            if (v.isWantsSponsor()) offers.add("Sponsor");
            if (v.isWantsChapter()) offers.add("Join a chapter");
            if (v.getOtherOffers() != null && !v.getOtherOffers().isBlank()) offers.add("Other");
            rows.add(row("signed_up", v.getCreatedAt(), "name", v.getFullName(), "email", v.getEmail(), "phone", v.getPhone(),
                    "city", v.getCity(), "country", v.getCountry(), "home_district", v.getHomeDistrict(), "profession", v.getProfession(),
                    "offers", String.join(", ", offers), "mentor_fields", v.getMentorFields(), "tutor_subjects", v.getTutorSubjects(),
                    "hours_per_month", v.getHoursPerMonth(), "chapter", chapters.get(v.getChapterId()),
                    "status", VolunteerApplication.STATUSES.getOrDefault(v.getStatus(), v.getStatus()), "notes", v.getNotes()));
        }
        return rows;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OnlinePaymentRepository onlinePaymentRepository;

    /** Paid online payments in the period (by payment date), oldest first: the details Form 10BD asks for. */
    private List<Map<String, Object>> onlinePayments80g(DateRange range) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (onlinePaymentRepository == null) return rows;
        onlinePaymentRepository.findAllByOrderByIdDesc().stream()
                .filter(p -> OnlinePayment.PAID.equals(p.getStatus()) && p.getPaidAt() != null && range.contains(p.getPaidAt()))
                .sorted(Comparator.comparing(OnlinePayment::getPaidAt))
                .forEach(p -> rows.add(row("receipt_no", p.getReceiptNo(), "date", p.getPaidAt().toLocalDate(), "name", p.getDonorName(),
                        "pan", p.getDonorPan(), "address", p.getDonorAddress(), "email", p.getDonorEmail(), "amount", p.getAmount(),
                        "mode", "Electronic (Razorpay)", "payment_id", p.getGatewayPaymentId(),
                        "towards", OnlinePayment.SPONSOR.equals(p.getPurpose()) ? "Scholarship (sponsor)" : p.getNote())));
        return rows;
    }

    // ---------------------------------------------------------------- payments

    private List<Map<String, Object>> payments(DateRange range, String onlyStatus) {
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        Map<Long, Integer> installmentNo = new HashMap<>(); // which installment each payment paid (amounts are never shown)
        installmentRepository.findAll().forEach(i -> { if (i.getPaymentId() != null) installmentNo.put(i.getPaymentId(), i.getInstallmentNo()); });
        List<Map<String, Object>> rows = new ArrayList<>();
        visiblePayments().stream()
                .filter(p -> range.contains(p.getPaymentDate()))
                .filter(p -> onlyStatus == null || onlyStatus.equalsIgnoreCase(p.getStatus()))
                .sorted(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(p -> rows.add(row(
                        "payment_id", p.getPaymentId(),
                        "payment_date", p.getPaymentDate(),
                        "student_id", codes.get(p.getGranteeId()),
                        "student_name", names.get(p.getGranteeId()),
                        "sponsor_id", codes.get(p.getGrantorId()),
                        "sponsor_name", names.get(p.getGrantorId()),
                        "installment", installmentNo.get(p.getPaymentId()),
                        "status", p.getStatus(),
                        "receipt", p.getReceiptUrl(),
                        "student_proof", p.getStudentProofUrl(),
                        "recorded_by", p.getCreatedBy() == null ? null : names.get(p.getCreatedBy()))));
        return rows;
    }

    private List<Map<String, Object>> paymentsByMonth(DateRange range) {
        Map<YearMonth, long[]> counts = new TreeMap<>(Comparator.reverseOrder());
        for (Payment p : visiblePayments()) {
            if (p.getPaymentDate() == null || !"Paid".equalsIgnoreCase(p.getStatus()) || !range.contains(p.getPaymentDate())) continue;
            YearMonth month = YearMonth.from(p.getPaymentDate());
            counts.computeIfAbsent(month, m -> new long[1])[0]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((month, c) -> rows.add(row("month", month.toString(), "installments_paid", c[0])));
        return rows;
    }

    private List<Map<String, Object>> paymentsBySponsor(DateRange range) {
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        paidCountsByGrantor(range).forEach((sponsorId, n) -> rows.add(row("sponsor_id", codes.get(sponsorId),
                "sponsor_name", names.get(sponsorId), "installments_paid", n)));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("installments_paid")).reversed());
        return rows;
    }

    /** Installments (paid payments) per sponsor. */
    private Map<Long, Long> paidCountsByGrantor(DateRange range) {
        Map<Long, Long> counts = new HashMap<>();
        for (Payment p : visiblePayments()) {
            if (!"Paid".equalsIgnoreCase(p.getStatus()) || !range.contains(p.getPaymentDate()) || p.getGrantorId() == null) continue;
            counts.merge(p.getGrantorId(), 1L, Long::sum);
        }
        return counts;
    }

    private List<Map<String, Object>> paymentSchedules() {
        Map<Long, String> names = userNames();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PaymentSchedule s : paymentScheduleRepository.findAllByOrderByYearDesc()) {
            rows.add(row("year", s.getYear(), "amount", s.getAmount(), "active", Integer.valueOf(1).equals(s.getStatus()) ? "Yes" : "No",
                    "updated_at", s.getUpdatedAt(),
                    "updated_by", s.getUpdatedBy() == null ? null : names.get(s.getUpdatedBy())));
        }
        return rows;
    }

    // ---------------------------------------------------------------- setup & system

    private List<Map<String, Object>> institutionsCourses() {
        Map<String, String> institutionNames = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutionNames.put(i.getInstitutionId(), i.getInstitutionName()));
        Map<Long, Long> enrolled = studentCourseRepository.findAll().stream()
                .collect(Collectors.groupingBy(StudentInstitutionCourse::getCourseId, Collectors.counting()));
        List<Map<String, Object>> rows = new ArrayList<>();
        courseRepository.findAll().stream()
                .sorted(Comparator.comparing((Course c) -> String.valueOf(institutionNames.get(c.getInstitutionId())))
                        .thenComparing(c -> String.valueOf(c.getCourseName())))
                .forEach(c -> rows.add(row(
                        "institution_id", c.getInstitutionId(),
                        "institution", institutionNames.get(c.getInstitutionId()),
                        "course_id", c.getCourseId(),
                        "course", c.getCourseName(),
                        "fees_per_semester", c.getFeesPerSemester(),
                        "semesters", c.getNumberOfSemesters(),
                        "students_enrolled", enrolled.getOrDefault(c.getCourseId(), 0L))));
        return rows;
    }

    private List<Map<String, Object>> rccCenters() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RccCenter c : sorted(rccCenterRepository.findAll(), RccCenter::getCenterName)) {
            rows.add(row("center_name", c.getCenterName(), "incharge", c.getInchargeName(),
                    "contact_number", c.getContactNumber(), "location", c.getLocation()));
        }
        return rows;
    }

    private List<Map<String, Object>> usersByRole() {
        Map<Integer, String> roleNames = roleNames();
        Map<Integer, long[]> counts = new TreeMap<>();
        for (User u : userRepository.findAll()) {
            long[] c = counts.computeIfAbsent(u.getRoleId(), r -> new long[2]);
            if ("Inactive".equalsIgnoreCase(u.getStatus())) c[1]++; else c[0]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((roleId, c) -> rows.add(row("role_id", roleId, "role", roleNames.getOrDefault(roleId, "Unknown"),
                "active_users", c[0], "inactive_users", c[1], "total", c[0] + c[1])));
        return rows;
    }

    private List<Map<String, Object>> activityLog(LocalDate from, LocalDate to) {
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActivityLog a : activityLogService.between(from, to)) {
            rows.add(row("when", a.getCreatedAt(), "user_id", codes.get(a.getUserId()), "user_name", a.getUserName(),
                    "action", a.getAction(), "method", a.getMethod(), "path", a.getPath(),
                    "result", a.getStatusCode(), "ip_address", a.getIpAddress()));
        }
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    /** Inclusive day range; null ends are open. */
    private record DateRange(LocalDate from, LocalDate to) {
        static final DateRange ALL = new DateRange(null, null);

        boolean contains(LocalDateTime t) {
            if (from == null && to == null) return true;
            if (t == null) return false;
            LocalDate d = t.toLocalDate();
            return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
        }
    }

    /** A row with the given column order: row("a", 1, "b", 2). */
    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) row.put((String) keyValues[i], keyValues[i + 1]);
        return row;
    }

    private static <T, K extends Comparable<? super K>> List<T> sorted(Collection<T> items, Function<T, K> key) {
        List<T> list = new ArrayList<>(items);
        list.sort(Comparator.comparing(key, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    /** users.id -> name. */
    private Map<Long, String> userNames() {
        Map<Long, String> names = new HashMap<>();
        userRepository.findAll().forEach(u -> names.put(u.getId(), u.getName()));
        return names;
    }

    /** users.id -> user code (users.user_id): reports show codes, not internal ids. */
    private Map<Long, String> userCodes() {
        Map<Long, String> codes = new HashMap<>();
        userRepository.findAll().forEach(u -> codes.put(u.getId(), u.getUserId()));
        return codes;
    }

    /** Student users.id -> sponsor users.id. */
    private Map<Long, Long> sponsorIdByStudent() {
        Map<Long, Long> map = new HashMap<>();
        grantorGranteeRepository.findAll().forEach(gg -> map.put(gg.getGranteeId(), gg.getGrantorId()));
        return map;
    }

    private Map<Integer, String> roleNames() {
        Map<Integer, String> names = new HashMap<>();
        roleRepository.findAll().forEach(r -> names.put(r.getRoleId(), r.getRoleName()));
        return names;
    }
}
