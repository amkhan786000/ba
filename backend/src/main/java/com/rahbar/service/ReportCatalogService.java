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

/**
 * Every report on the admin Reports page. Each report is a list of rows with a fixed column order,
 * rendered as CSV / Excel / PDF by {@link ReportService}. Reports that have a date make use of the
 * optional from / to range (inclusive days).
 */
@Service
public class ReportCatalogService {

    /** One entry of the catalog shown on the Reports page. */
    public record Definition(String key, String category, String title, String description, boolean dateRange) {}

    private static final List<Definition> CATALOG = List.of(
            new Definition("applications", "Applications", "All applications",
                    "Every application with contact numbers, latest status, interview and the sponsor of the student.", true),
            new Definition("applications_by_status", "Applications", "Applications by status",
                    "How many applications are in each status (latest status of each application).", true),
            new Definition("interviews", "Applications", "Interview schedule",
                    "Applications with a scheduled interview, soonest first.", true),

            new Definition("grantees", "Students", "Student directory",
                    "Every student with sponsor, institution, course and whether bank details are on file.", false),
            new Definition("students_without_sponsor", "Students", "Students without a sponsor",
                    "Active students who are not mapped to a sponsor (or parked on the unassigned account).", false),
            new Definition("students_without_bank", "Students", "Students without bank details",
                    "Active students who have no bank details on file yet.", false),
            new Definition("student_progress", "Students", "Progress reports",
                    "Marks uploaded by students with the review status and reviewer comments.", true),

            new Definition("students_by_institution", "Student breakdowns", "Students by institution",
                    "Number of students at each college: active, inactive, with and without a sponsor.", false),
            new Definition("students_by_course", "Student breakdowns", "Students by course",
                    "Number of students in each course (with its college, length and fees), including empty courses.", false),
            new Definition("students_by_chapter", "Student breakdowns", "Students by chapter",
                    "Number of students in each chapter.", false),
            new Definition("students_by_year", "Student breakdowns", "Students by academic year",
                    "Number of students in each academic year.", false),
            new Definition("students_by_study_status", "Student breakdowns", "Students by study status",
                    "Number of students studying, on hold, graduated and dropped out.", false),
            new Definition("applications_by_course", "Student breakdowns", "Applications by course applied",
                    "Demand before admission: applications per course applied for, with accepted / admitted and rejected counts.", true),
            new Definition("applications_by_rcc", "Student breakdowns", "Applications by RCC center",
                    "Applications per RCC center, with accepted / admitted and rejected counts.", true),

            new Definition("sponsors_convenors", "Sponsors", "Sponsors & convenors",
                    "Every sponsor and convenor with number of students and total paid.", false),
            new Definition("sponsors_by_chapter", "Sponsors", "Sponsors by chapter",
                    "Number of sponsors, students and total paid per chapter.", false),

            new Definition("payments", "Payments", "All payments",
                    "Every payment with student, sponsor, amount, status and date.", true),
            new Definition("payments_by_month", "Payments", "Payments by month",
                    "Number and total of paid payments per month.", true),
            new Definition("payments_by_sponsor", "Payments", "Payments by sponsor",
                    "Number and total of paid payments per sponsor.", true),
            new Definition("pending_payments", "Payments", "Payments awaiting approval",
                    "Payments recorded with a pending status.", true),
            new Definition("payment_dues", "Payments", "Payment dues",
                    "Installments due vs paid for every sponsored student, with overdue counts and next due date.", false),
            new Definition("payment_schedules", "Payments", "Payment amount by year",
                    "The configured scholarship amount for each academic year.", false),

            new Definition("institutions_courses", "Setup", "Institutions & courses",
                    "Every course with its institution, fees, length and number of students enrolled.", false),
            new Definition("rcc_centers", "Setup", "RCC centers", "All RCC centers with incharge and contact.", false),
            new Definition("users_by_role", "Setup", "Users by role",
                    "Number of active and inactive users in each role.", false),

            new Definition("activity_log", "System", "Activity log",
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
                                ActivityLogService activityLogService, ReportService reportService) {
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

    public List<Definition> catalog() {
        return CATALOG;
    }

    /** Builds a report download. from / to are optional inclusive days (ignored by reports without a date). */
    public ReportService.Report build(String key, String format, LocalDate from, LocalDate to) {
        Definition def = definition(key);
        String name = key + "_report" + (def.dateRange() && (from != null || to != null)
                ? "_" + (from == null ? "start" : from) + "_to_" + (to == null ? "today" : to) : "");
        return reportService.build(privateRows(key, from, to), name, format, def.title());
    }

    /** The report's rows for viewing on screen: { title, columns, rows } (an empty report has no columns). */
    public Map<String, Object> data(String key, LocalDate from, LocalDate to) {
        Definition def = definition(key);
        List<Map<String, Object>> rows = privateRows(key, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("title", def.title());
        body.put("columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
        body.put("rows", rows);
        return body;
    }

    private Definition definition(String key) {
        return CATALOG.stream().filter(d -> d.key().equals(key)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type selected."));
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
            case "payments" -> payments(range, null);
            case "pending_payments" -> payments(range, "pending");
            case "payments_by_month" -> paymentsByMonth(range);
            case "payments_by_sponsor" -> paymentsBySponsor(range);
            case "payment_dues" -> paymentReminderService.dues();
            case "payment_schedules" -> paymentSchedules();
            case "institutions_courses" -> institutionsCourses();
            case "students_by_institution" -> studentsByInstitution();
            case "students_by_course" -> studentsByCourse();
            case "students_by_chapter" -> studentsBy(u -> ServiceSupport.chapterLabel(u.getChapterName()), "chapter");
            case "students_by_study_status" -> studentsBy(u -> StudyStatus.label(u.getStudyStatus()), "study_status");
            case "students_by_year" -> studentsBy(u -> u.getYear() == null ? "Not set" : String.valueOf(u.getYear()), "academic_year");
            case "applications_by_course" -> applicationsBy(GranteeDetails::getCourseApplied, "course_applied", range);
            case "applications_by_rcc" -> applicationsBy(GranteeDetails::getRccName, "rcc_center", range);
            case "rcc_centers" -> rccCenters();
            case "users_by_role" -> usersByRole();
            case "activity_log" -> activityLog(from, to);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type selected.");
        };
    }

    // ---------------------------------------------------------------- applications

    private List<Map<String, Object>> applications(DateRange range) {
        Map<Long, ApplicationStatus> latest = ServiceSupport.latestByApplication(applicationStatusRepository.findLatestPerApplication());
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GranteeDetails g : sorted(granteeDetailsRepository.findAll(), GranteeDetails::getGranteeDetailId)) {
            if (!range.contains(g.getCreatedAt())) continue;
            ApplicationStatus s = latest.get(g.getGranteeDetailId());
            Long sponsorId = g.getUserId() == null ? null : sponsorOf.get(g.getUserId());
            rows.add(row(
                    "application_id", g.getGranteeDetailId(),
                    "student_name", g.getName(),
                    "student_user_id", codes.get(g.getUserId()),
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
                    "status", s == null ? "no status" : s.getStatus(),
                    "status_date", s == null ? null : s.getCreatedAt(),
                    "interview_at", g.getInterviewAt(),
                    "interview_venue", g.getInterviewVenue(),
                    "assigned_sponsor_id", codes.get(sponsorId),
                    "assigned_sponsor_name", sponsorId == null ? null : names.get(sponsorId)));
        }
        return rows;
    }

    private List<Map<String, Object>> applicationsByStatus(DateRange range) {
        Map<Long, ApplicationStatus> latest = ServiceSupport.latestByApplication(applicationStatusRepository.findLatestPerApplication());
        Map<String, Long> counts = new TreeMap<>();
        for (GranteeDetails g : granteeDetailsRepository.findAll()) {
            if (!range.contains(g.getCreatedAt())) continue;
            ApplicationStatus s = latest.get(g.getGranteeDetailId());
            counts.merge(s == null || s.getStatus() == null ? "no status" : s.getStatus(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((status, count) -> rows.add(row("status", status, "applications", count)));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("applications")).reversed());
        return rows;
    }

    private List<Map<String, Object>> interviews(DateRange range) {
        List<Map<String, Object>> rows = new ArrayList<>();
        granteeDetailsRepository.findAll().stream()
                .filter(g -> g.getInterviewAt() != null && range.contains(g.getInterviewAt()))
                .sorted(Comparator.comparing(GranteeDetails::getInterviewAt))
                .forEach(g -> rows.add(row(
                        "interview_at", g.getInterviewAt(),
                        "venue", g.getInterviewVenue(),
                        "application_id", g.getGranteeDetailId(),
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
        for (User u : sorted(userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE), User::getUserId)) {
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
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
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
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
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

    /** Students grouped by any label (chapter, academic year...), biggest group first. */
    private List<Map<String, Object>> studentsBy(Function<User, String> label, String column) {
        Map<Long, Long> sponsorOf = sponsorIdByStudent();
        Long unassignedGrantor = ServiceSupport.unassignedGrantorId(userRepository);
        Map<String, Tally> tallies = new HashMap<>();
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
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
    private List<Map<String, Object>> applicationsBy(Function<GranteeDetails, String> field, String column, DateRange range) {
        Map<Long, ApplicationStatus> latest = ServiceSupport.latestByApplication(applicationStatusRepository.findLatestPerApplication());
        Map<String, long[]> counts = new HashMap<>();   // [applications, accepted/admitted, rejected]
        Map<String, String> display = new HashMap<>();  // first spelling seen for each case-insensitive key
        for (GranteeDetails g : granteeDetailsRepository.findAll()) {
            if (!range.contains(g.getCreatedAt())) continue;
            String value = field.apply(g);
            String shown = value == null || value.isBlank() ? "Not given" : value.trim().replaceAll("\\s+", " ");
            String key = shown.toLowerCase(Locale.ROOT);
            display.putIfAbsent(key, shown);
            long[] c = counts.computeIfAbsent(key, k -> new long[3]);
            c[0]++;
            ApplicationStatus s = latest.get(g.getGranteeDetailId());
            String status = s == null || s.getStatus() == null ? "" : s.getStatus().toLowerCase(Locale.ROOT);
            if (status.equals("accepted") || status.equals("admitted") || status.equals("provisional admission letter")) c[1]++;
            if (status.equals("rejected")) c[2]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((key, c) -> rows.add(row(column, display.get(key), "applications", c[0],
                "accepted_or_admitted", c[1], "rejected", c[2], "in_progress", c[0] - c[1] - c[2])));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("applications")).reversed()
                .thenComparing(r -> String.valueOf(r.get(column)), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    // ---------------------------------------------------------------- sponsors

    private List<Map<String, Object>> sponsors() {
        Map<Long, Long> studentCount = grantorGranteeRepository.findAll().stream()
                .collect(Collectors.groupingBy(GrantorGrantee::getGrantorId, Collectors.counting()));
        Map<Long, BigDecimal> paid = paidTotalsByGrantor(DateRange.ALL);
        Map<Integer, String> roleNames = roleNames();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : sorted(userRepository.findByRoleIdIn(ServiceSupport.SPONSOR_CONVENOR_ROLES), User::getName)) {
            rows.add(row(
                    "user_id", u.getUserId(),
                    "name", u.getName(),
                    "role", roleNames.get(u.getRoleId()),
                    "email", u.getEmail(),
                    "phone", u.getPhone(),
                    "chapter", u.getChapterName(),
                    "status", u.getStatus(),
                    "students", studentCount.getOrDefault(u.getId(), 0L),
                    "total_paid", paid.getOrDefault(u.getId(), BigDecimal.ZERO),
                    "joined", u.getCreatedAt()));
        }
        return rows;
    }

    private List<Map<String, Object>> sponsorsByChapter() {
        Map<Long, Long> studentCount = grantorGranteeRepository.findAll().stream()
                .collect(Collectors.groupingBy(GrantorGrantee::getGrantorId, Collectors.counting()));
        Map<Long, BigDecimal> paid = paidTotalsByGrantor(DateRange.ALL);
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (User u : userRepository.findByRoleId(5)) {
            String chapter = ServiceSupport.chapterLabel(u.getChapterName());
            long[] c = counts.computeIfAbsent(chapter, r -> new long[2]);
            c[0]++;
            c[1] += studentCount.getOrDefault(u.getId(), 0L);
            totals.merge(chapter, paid.getOrDefault(u.getId(), BigDecimal.ZERO), BigDecimal::add);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((chapter, c) -> rows.add(row("chapter", chapter, "sponsors", c[0], "students", c[1],
                "total_paid", totals.getOrDefault(chapter, BigDecimal.ZERO))));
        return rows;
    }

    // ---------------------------------------------------------------- payments

    private List<Map<String, Object>> payments(DateRange range, String onlyStatus) {
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        List<Map<String, Object>> rows = new ArrayList<>();
        paymentRepository.findAll().stream()
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
                        "amount", p.getAmount(),
                        "status", p.getStatus(),
                        "receipt", p.getReceiptUrl(),
                        "student_proof", p.getStudentProofUrl(),
                        "recorded_by", p.getCreatedBy() == null ? null : names.get(p.getCreatedBy()))));
        return rows;
    }

    private List<Map<String, Object>> paymentsByMonth(DateRange range) {
        Map<YearMonth, long[]> counts = new TreeMap<>(Comparator.reverseOrder());
        Map<YearMonth, BigDecimal> totals = new HashMap<>();
        for (Payment p : paymentRepository.findAll()) {
            if (p.getPaymentDate() == null || !"Paid".equalsIgnoreCase(p.getStatus()) || !range.contains(p.getPaymentDate())) continue;
            YearMonth month = YearMonth.from(p.getPaymentDate());
            counts.computeIfAbsent(month, m -> new long[1])[0]++;
            totals.merge(month, p.getAmount() == null ? BigDecimal.ZERO : p.getAmount(), BigDecimal::add);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((month, c) -> rows.add(row("month", month.toString(), "payments", c[0], "total_amount", totals.get(month))));
        return rows;
    }

    private List<Map<String, Object>> paymentsBySponsor(DateRange range) {
        Map<Long, String> names = userNames();
        Map<Long, String> codes = userCodes();
        Map<Long, long[]> counts = new HashMap<>();
        Map<Long, BigDecimal> totals = paidTotalsByGrantor(range);
        for (Payment p : paymentRepository.findAll()) {
            if (!"Paid".equalsIgnoreCase(p.getStatus()) || !range.contains(p.getPaymentDate())) continue;
            counts.computeIfAbsent(p.getGrantorId(), g -> new long[1])[0]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((sponsorId, c) -> rows.add(row("sponsor_id", codes.get(sponsorId), "sponsor_name", names.get(sponsorId),
                "payments", c[0], "total_amount", totals.getOrDefault(sponsorId, BigDecimal.ZERO))));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("total_amount")).reversed());
        return rows;
    }

    private Map<Long, BigDecimal> paidTotalsByGrantor(DateRange range) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Payment p : paymentRepository.findAll()) {
            if (!"Paid".equalsIgnoreCase(p.getStatus()) || !range.contains(p.getPaymentDate()) || p.getGrantorId() == null) continue;
            totals.merge(p.getGrantorId(), p.getAmount() == null ? BigDecimal.ZERO : p.getAmount(), BigDecimal::add);
        }
        return totals;
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
