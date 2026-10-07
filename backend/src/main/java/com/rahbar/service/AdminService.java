package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.ServiceSupport.*;

/**
 * Admin (roles 1, 2) and Office Coordinator (role 8) screens: dashboard, application period, users,
 * payment configuration, RCC centers, courses, applications, students, sponsors, payments and reports.
 *
 * No class-level transaction on purpose: bulk uploads save row by row and skip bad rows, like the original.
 */
@Service
public class AdminService {

    /** Initial password of accounts created by an admin, bulk upload or manual add (must be changed at first sign-in). */
    private static final String DEFAULT_PASSWORD = PasswordPolicy.DEFAULT_PASSWORD;

    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final RoleRepository roleRepository;
    private final ApplicationPeriodRepository applicationPeriodRepository;
    private final PaymentScheduleRepository paymentScheduleRepository;
    private final RccCenterRepository rccCenterRepository;
    private final CourseRepository courseRepository;
    private final InstitutionRepository institutionRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final PaymentRepository paymentRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final SponsorMappingService sponsorMappingService;
    private final StudentService studentService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final com.rahbar.security.SponsorPrivacy sponsorPrivacy;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public AdminService(UserRepository userRepository, ChapterRepository chapterRepository, RoleRepository roleRepository,
                        ApplicationPeriodRepository applicationPeriodRepository,
                        PaymentScheduleRepository paymentScheduleRepository,
                        RccCenterRepository rccCenterRepository, CourseRepository courseRepository,
                        InstitutionRepository institutionRepository,
                        GranteeDetailsRepository granteeDetailsRepository,
                        ApplicationStatusRepository applicationStatusRepository,
                        GrantorGranteeRepository grantorGranteeRepository,
                        StudentInstitutionCourseRepository studentCourseRepository,
                        BankDetailsRepository bankDetailsRepository, PaymentRepository paymentRepository,
                        StudentProgressRepository studentProgressRepository,
                        SponsorMappingService sponsorMappingService, StudentService studentService,
                        ApplicationService applicationService, NotificationService notificationService,
                        PasswordEncoder passwordEncoder,
                        FileStorageService fileStorageService, EmailService emailService,
                        com.rahbar.security.SponsorPrivacy sponsorPrivacy) {
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.roleRepository = roleRepository;
        this.applicationPeriodRepository = applicationPeriodRepository;
        this.paymentScheduleRepository = paymentScheduleRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.courseRepository = courseRepository;
        this.institutionRepository = institutionRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.paymentRepository = paymentRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.sponsorMappingService = sponsorMappingService;
        this.studentService = studentService;
        this.applicationService = applicationService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.sponsorPrivacy = sponsorPrivacy;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    // ---------------------------------------------------------------- dashboard

    public Map<String, Object> dashboard(Integer year) {
        List<Integer> years = yearsDesc(userRepository.findCreatedYears(), granteeDetailsRepository.findCreatedYears(),
                paymentRepository.findPaymentYears());

        long usersCount = year == null ? userRepository.count() : userRepository.countCreatedInYear(year);
        long applicationsCount = year == null ? granteeDetailsRepository.count() : granteeDetailsRepository.countCreatedInYear(year);
        long paymentsCount = year == null ? paymentRepository.count() : paymentRepository.countPaidInYear(year);
        long sponsorsConvenorsCount = year == null
                ? userRepository.countByRoleIdInAndStatus(SPONSOR_CONVENOR_ROLES, "Active")
                : userRepository.countByRoleIdInAndStatusCreatedInYear(SPONSOR_CONVENOR_ROLES, "Active", year);

        Map<String, Object> applicationPeriod = applicationPeriodRepository.findById(1L)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(Rows::of).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("usersCount", usersCount);
        result.put("applicationsCount", applicationsCount);
        result.put("paymentsCount", paymentsCount);
        result.put("sponsorsConvenorsCount", sponsorsConvenorsCount);
        result.put("availableYears", years);
        result.put("selectedYear", year);
        result.put("applicationPeriod", applicationPeriod);
        return result;
    }

    // ---------------------------------------------------------- application period

    public Optional<ApplicationPeriod> currentApplicationPeriod() {
        return applicationPeriodRepository.findFirstByIsActiveTrueOrderByStartDateDesc();
    }

    public void startApplicationPeriod(String startDate, String endDate) {
        ApplicationPeriod period = new ApplicationPeriod();
        period.setStartDate(LocalDate.parse(startDate).atStartOfDay());
        period.setEndDate(LocalDate.parse(endDate).atStartOfDay());
        if (period.getEndDate().isBefore(period.getStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before the start date.");
        }
        period.setIsActive(true);
        applicationPeriodRepository.save(period);
    }

    /** Ends every active period; returns how many were ended. */
    public int endApplicationPeriods() {
        List<ApplicationPeriod> active = applicationPeriodRepository.findByIsActiveTrue();
        active.forEach(p -> p.setIsActive(false));
        applicationPeriodRepository.saveAll(active);
        return active.size();
    }

    // ---------------------------------------------------------------- manage users

    /** One page of users (with role_name) ordered by user_id (the code); every filter is optional. */
    public Map<String, Object> listUsers(int page, int size, String name, String email, Integer roleId, String status) {
        Map<Integer, String> roleNames = roleNames();
        org.springframework.data.domain.Page<User> result = userRepository.searchUsers(likePattern(name), likePattern(email), roleId,
                isBlank(status) ? null : status.trim().toLowerCase(Locale.ROOT),
                pageRequest(page, size, org.springframework.data.domain.Sort.by("userId")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : result.getContent()) {
            Map<String, Object> row = Rows.of(u);
            row.put("role_name", roleNames.get(u.getRoleId()));
            rows.add(row);
        }
        return pageBody(result, rows);
    }

    /** One user (with role_name) for the edit page. */
    public Map<String, Object> getUser(Long userId) {
        User u = requireUser(userRepository, userId, "User not found");
        Map<String, Object> row = Rows.of(u);
        row.put("role_name", roleRepository.findById(u.getRoleId()).map(Role::getRoleName).orElse(null));
        return row;
    }

    private Map<Integer, String> roleNames() {
        Map<Integer, String> names = new HashMap<>();
        roleRepository.findAll().forEach(r -> names.put(r.getRoleId(), r.getRoleName()));
        return names;
    }

    public void createUser(Map<String, Object> body) {
        User user = new User();
        // Blank code: next number after the highest numeric user code (what the Add User form used to work out itself).
        String code = isBlank(body.get("userId")) ? nextNumericUserId() : String.valueOf(body.get("userId")).trim();
        if (userRepository.existsByUserId(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "User ID " + code + " is already taken.");
        }
        Integer roleId = Integer.valueOf(String.valueOf(body.get("roleId")));
        requireMayAssignRole(roleId);
        user.setUserId(code);
        user.setName(String.valueOf(body.get("name")));
        user.setEmail(trimToNull(str(body.get("email"))));   // optional; blank stays NULL so it doesn't clash as ''
        user.setPhone(trimToNull(str(body.get("contact"))));
        user.setRoleId(roleId);
        user.setStatus(String.valueOf(body.getOrDefault("status", "Active")));
        user.setChapterId(requireChapter(chapterRepository, body.get("chapterId"))); // optional
        user.setRccCenterId(requireRccCenter(body.get("rccCenterId")));              // optional
        user.setSex(String.valueOf(body.getOrDefault("sex", "M")));
        user.setPasswordHash(passwordEncoder.encode(String.valueOf(body.get("password"))));
        user.setMustChangePassword(true); // the admin chose this password; the user picks their own at first sign-in
        userRepository.save(user);
    }

    private String nextNumericUserId() {
        long max = userRepository.findAllUserIds().stream()
                .filter(id -> id != null && id.matches("\\d{1,15}"))
                .mapToLong(Long::parseLong).max().orElse(1000);
        return String.valueOf(max + 1);
    }

    public void updateUser(Long userId, Map<String, Object> body) {
        User user = requireUser(userRepository, userId, "User not found");
        requireMayAssignRole(user.getRoleId()); // only a Super Admin may change a Super Admin
        boolean hidden = hidesDetailsOf(user); // a sponsor's details the editor isn't allowed to see stay as they are
        if (body.get("name") != null) user.setName(String.valueOf(body.get("name")));
        if (!hidden && body.containsKey("email")) user.setEmail(trimToNull(str(body.get("email"))));
        if (body.get("roleId") != null) {
            Integer roleId = Integer.valueOf(String.valueOf(body.get("roleId")));
            requireMayAssignRole(roleId);
            user.setRoleId(roleId);
        }
        if (body.get("status") != null) user.setStatus(String.valueOf(body.get("status")));
        if (!hidden && body.containsKey("chapterId")) user.setChapterId(requireChapter(chapterRepository, body.get("chapterId")));
        if (!hidden && body.containsKey("rccCenterId")) user.setRccCenterId(requireRccCenter(body.get("rccCenterId")));
        userRepository.save(user);
    }

    /** True for a sponsor whose details the signed-in user may not see (they were blanked on their screen). */
    private boolean hidesDetailsOf(User user) {
        return Integer.valueOf(5).equals(user.getRoleId()) && sponsorPrivacy.hidesDetails();
    }

    /**
     * Gives the user a new random temporary password (to be changed at their next sign-in) and emails it to them.
     * When they have no usable email, or sending fails, the password is returned so the admin can pass it on.
     */
    public Map<String, Object> resetUserPassword(Long userId) {
        User user = requireUser(userRepository, userId, "User not found");
        requireMayAssignRole(user.getRoleId()); // only a Super Admin may reset a Super Admin
        String password = PasswordPolicy.temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        userRepository.save(user);

        String email = user.getEmail();
        boolean usableEmail = email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
        boolean emailed = usableEmail && emailService.send(email, "Rahbar: your password was reset",
                "Dear " + user.getName() + ",\n\n"
                        + "An administrator reset your Rahbar password. Sign in with this temporary password:\n\n"
                        + "    " + password + "\n\n"
                        + "You will be asked to choose your own password straight after signing in.\n\n"
                        + "Regards,\nRahbar - Bihar Anjuman");
        notificationService.notify(user.getId(), "Password reset",
                "An administrator reset your password. Use the temporary password you were given and choose a new one.",
                NotificationService.ACCOUNT, null, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("emailed", emailed);
        if (emailed) {
            result.put("message", "The password was reset and a temporary password was emailed to " + email + ".");
        } else {
            result.put("message", usableEmail
                    ? "The password was reset, but the email could not be sent. Give the user this temporary password."
                    : "The password was reset. " + user.getName() + " has no email address, so give them this temporary password.");
            result.put("temporaryPassword", password);
        }
        return result;
    }

    /** Only a Super Admin may create Super Admins, give someone that role, or change a Super Admin's account. */
    private static void requireMayAssignRole(Integer roleId) {
        if (Integer.valueOf(Role.SUPER_ADMIN).equals(roleId) && !Access.isSuperAdmin()) {
            throw Access.forbidden("Only a Super Admin can manage Super Admin accounts.");
        }
    }

    /** An RCC center id from a request (null when none was chosen); 400 when it doesn't exist. */
    private Long requireRccCenter(Object value) {
        Long id = toLong(value);
        if (id != null && !rccCenterRepository.existsById(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The selected RCC center does not exist.");
        }
        return id;
    }

    // --------------------------------------------------------- system configuration

    /** Payment amount per year, newest first, with the name of who last changed it. */
    public Map<String, Object> systemConfiguration() {
        List<PaymentSchedule> schedules = paymentScheduleRepository.findAllByOrderByYearDesc();
        Map<Long, String> names = userNames(userRepository, schedules.stream().map(PaymentSchedule::getUpdatedBy).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PaymentSchedule s : schedules) {
            Map<String, Object> row = Rows.pick(s, "schedule_id", "amount", "year", "updated_at");
            row.put("updated_by_name", s.getUpdatedBy() == null ? null : names.get(s.getUpdatedBy()));
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schedules", rows);
        return result;
    }

    public void saveSchedule(int year, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount cannot be negative.");
        }
        PaymentSchedule schedule = paymentScheduleRepository.findByYear(year).orElseGet(PaymentSchedule::new);
        schedule.setYear(year);
        schedule.setAmount(amount);
        schedule.setStatus(1);
        paymentScheduleRepository.save(schedule);
    }

    // ---------------------------------------------------------------- RCC centers

    /** All RCC centers; an RCC coordinator (RCC scope) only sees their own. */
    public List<RccCenter> listRccCenters() {
        if (Access.rccScoped()) {
            Long own = Access.current().getUser().getRccCenterId();
            return own == null ? List.of() : rccCenterRepository.findById(own).map(List::of).orElse(List.of());
        }
        return rccCenterRepository.findAll();
    }

    /** RCC coordinators may only edit their own center and may not add new ones. */
    public RccCenter saveRccCenter(RccCenter center) {
        if (Access.rccScoped()) {
            Long own = Access.current().getUser().getRccCenterId();
            if (center.getRccCenterId() == null || !center.getRccCenterId().equals(own)) {
                throw Access.forbidden("You can only edit your own RCC center.");
            }
        }
        RccCenter saved = rccCenterRepository.save(center);
        // Re-read so the response carries the stored created_at / created_by on edits too.
        return rccCenterRepository.findById(saved.getRccCenterId()).orElse(saved);
    }

    public void deleteRccCenter(Long id) {
        if (Access.rccScoped()) throw Access.forbidden("You can only edit your own RCC center.");
        rccCenterRepository.deleteById(id);
    }

    // --------------------------------------------------------------------- courses

    /** Every course with its institution_name (courses of unknown institutions are left out). */
    public List<Map<String, Object>> listCourses() {
        Map<String, String> institutionNames = new HashMap<>();
        institutionRepository.findAll().forEach(i -> institutionNames.put(i.getInstitutionId(), i.getInstitutionName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Course c : courseRepository.findAll()) {
            if (!institutionNames.containsKey(c.getInstitutionId())) continue;
            Map<String, Object> row = Rows.of(c);
            row.put("institution_name", institutionNames.get(c.getInstitutionId()));
            rows.add(row);
        }
        return rows;
    }

    public List<Course> coursesByInstitution(String institutionId) {
        return courseRepository.findByInstitutionId(institutionId);
    }

    public Course saveCourse(Course course) {
        return courseRepository.save(course);
    }

    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
    }

    public List<Institution> listInstitutions() {
        return institutionRepository.findAll();
    }

    /** Adds an institution; without an id one is generated (INST-101, INST-102, ...). Returns the id. */
    public String addInstitution(Map<String, Object> body) {
        String institutionId = isBlank(body.get("institutionId"))
                ? "INST-" + (institutionRepository.count() + 101)
                : String.valueOf(body.get("institutionId"));
        Institution institution = new Institution();
        institution.setInstitutionId(institutionId);
        institution.setInstitutionName(String.valueOf(body.get("institutionName")));
        institution.setAddress(str(body.get("address")));
        institution.setContactNumber(str(body.get("contactNumber")));
        institution.setEmail(str(body.get("email")));
        institutionRepository.save(institution);
        return institutionId;
    }

    // ------------------------------------------------------------------ applications

    /** One page of applications (newest first) with their latest status; filters are optional. */
    public Map<String, Object> applications(int page, int size, String name, String status, String rcc) {
        org.springframework.data.domain.Page<GranteeDetails> result = granteeDetailsRepository.searchApplications(
                likePattern(name), likePattern(rcc), isBlank(status) ? null : status.trim().toLowerCase(Locale.ROOT),
                pageRequest(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "granteeDetailId")));
        List<Long> ids = result.getContent().stream().map(GranteeDetails::getGranteeDetailId).toList();
        Map<Long, ApplicationStatus> latest = ids.isEmpty() ? Map.of()
                : latestByApplication(applicationStatusRepository.findLatestFor(ids));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GranteeDetails gd : result.getContent()) {
            ApplicationStatus s = latest.get(gd.getGranteeDetailId());
            Map<String, Object> row = Rows.of(gd);
            row.put("status", s == null ? null : s.getStatus());
            row.put("comments", s == null ? null : s.getComments());
            row.put("status_date", s == null ? null : s.getCreatedAt());
            rows.add(row);
        }
        return pageBody(result, rows);
    }

    public void updateApplicationStatus(Long granteeDetailId, String status, String comments) {
        applicationService.updateStatus(granteeDetailId, status, comments);
    }

    // ------------------------------------------------------------------ manage students

    public List<Map<String, Object>> manageStudents() {
        return userRepository.findStudentOverview();
    }

    public void assignStudentCourse(Long userId, String institutionId, Long courseId) {
        if (!courseRepository.existsByCourseIdAndInstitutionId(courseId, institutionId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid course-institution combination");
        }
        StudentInstitutionCourse sic = studentCourseRepository.findById(userId).orElseGet(() -> {
            StudentInstitutionCourse n = new StudentInstitutionCourse();
            n.setUserId(userId);
            return n;
        });
        sic.setInstitutionId(institutionId);
        sic.setCourseId(courseId);
        sic.setAssignedBy(me());
        sic.setAssignedAt(LocalDateTime.now());
        studentCourseRepository.save(sic);
    }

    /** Sets the student's institution and course; assigned_by / assigned_at are only set on first assignment. */
    private void saveStudentCourse(Long userId, String institutionId, Long courseId) {
        StudentInstitutionCourse sic = studentCourseRepository.findById(userId).orElseGet(() -> {
            StudentInstitutionCourse n = new StudentInstitutionCourse();
            n.setUserId(userId);
            n.setAssignedBy(me());
            n.setAssignedAt(LocalDateTime.now());
            return n;
        });
        sic.setInstitutionId(institutionId);
        sic.setCourseId(courseId);
        studentCourseRepository.save(sic);
    }

    // --------------------------------------------------------------- sponsorships

    /** Active coordinators / convenors / sponsors with their student count; contact info hidden when asked. */
    public List<Map<String, Object>> sponsorships(boolean hideContactInfo) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : userRepository.findActiveSponsorships()) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            if (hideContactInfo) {
                row.remove("email");
                row.remove("phone");
            }
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> sponsorMappingScreen(Long sponsorId, boolean hideContactInfo) {
        Map<String, Object> sponsor = Rows.pick(requireUser(userRepository, sponsorId, "Sponsor not found"),
                "id", "user_id", "name", "email", "chapter_id", "chapter_name");
        if (hideContactInfo) sponsor.remove("email");

        List<User> mapped = new ArrayList<>(userRepository.findGranteesOf(sponsorId).stream()
                .filter(u -> "active".equalsIgnoreCase(u.getStatus())).toList());
        mapped.sort(Comparator.comparing(User::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sponsor", sponsor);
        result.put("mappedStudents", Rows.pickAll(mapped, "id", "user_id", "name", "email", "phone", "chapter_id", "chapter_name"));
        result.put("availableStudents", userRepository.findStudentsAvailableFor(sponsorId));
        return result;
    }

    public void mapStudentsToSponsor(Long sponsorId, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select at least one student.");
        }
        for (Long studentId : studentIds) {
            sponsorMappingService.map(studentId, sponsorId, "Accepted", false);
        }
    }

    // ------------------------------------------------------------- student directory

    /** Student directory page (DataTables shape: recordsTotal, recordsFiltered, data). */
    public Map<String, Object> listStudents(int start, int length, String search, String institutionId, Long courseId) {
        String pattern = isBlank(search) ? null : "%" + search.trim() + "%";
        List<Map<String, Object>> filtered = userRepository.searchStudentDirectory(
                isBlank(institutionId) ? null : institutionId, courseId, pattern);
        int from = Math.min(Math.max(start, 0), filtered.size());
        int to = Math.min(from + Math.max(length, 0), filtered.size());
        return Map.of("recordsTotal", userRepository.countByRoleId(STUDENT_ROLE),
                "recordsFiltered", (long) filtered.size(),
                "data", new ArrayList<>(filtered.subList(from, to)));
    }

    public Map<String, Object> studentDetails(Long userId) {
        User user = requireUser(userRepository, userId, "Student not found");
        GranteeDetails gd = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(userId).orElse(null);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_real_name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("status", user.getStatus());
        profile.put("chapter_id", user.getChapterId());
        profile.put("chapter_name", user.getChapterName());
        profile.put("year", user.getYear());
        profile.putAll(gd != null ? Rows.of(gd) : Rows.empty(GranteeDetails.class));
        profile.put("id", user.getId());
        profile.put("user_id", user.getUserId());
        profile.put("name", user.getName());
        profile.put("annual_schedule_amount", user.getYear() == null ? 0 : paymentScheduleRepository
                .findFirstByYearAndStatus(user.getYear(), 1).map(s -> (Object) s.getAmount()).orElse(0));

        List<Payment> payments = paymentRepository.findByGranteeIdOrderByPaymentDateDesc(userId);
        Map<Long, String> grantorNames = userNames(userRepository, payments.stream().map(Payment::getGrantorId).toList());
        List<Map<String, Object>> paymentRows = new ArrayList<>();
        for (Payment p : payments) {
            Map<String, Object> row = Rows.of(p);
            row.put("grantor_name", grantorNames.get(p.getGrantorId()));
            paymentRows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile);
        result.put("bank", bankDetailsRepository.findFirstByUserId(userId).map(Rows::of).orElse(null));
        result.put("course", Rows.first(studentCourseRepository.findCourseDetails(userId)));
        result.put("sponsor", grantorGranteeRepository.findFirstByGranteeId(userId)
                .flatMap(gg -> userRepository.findById(gg.getGrantorId()))
                .map(u -> Rows.pick(u, "id", "user_id", "name", "email")).orElse(null));
        result.put("payments", paymentRows);
        result.put("documents", Rows.list(studentProgressRepository.findByGranteeIdOrderByCreatedAtDesc(userId)));
        return result;
    }

    /** Partial update of a student's account, family details, bank details and course (only the fields sent). */
    public void updateStudent(Long userId, Map<String, Object> data) {
        userRepository.findById(userId).ifPresent(user -> {
            boolean changed = false;
            if (data.get("name") != null) { user.setName(str(data.get("name"))); changed = true; }
            if (data.get("email") != null) { user.setEmail(str(data.get("email"))); changed = true; }
            if (data.get("phone") != null) { user.setPhone(str(data.get("phone"))); changed = true; }
            if (data.containsKey("chapterId")) { user.setChapterId(requireChapter(chapterRepository, data.get("chapterId"))); changed = true; }
            if (data.get("status") != null) { user.setStatus(str(data.get("status"))); changed = true; }
            if (changed) userRepository.save(user);
        });

        updateFamilyDetails(userId, data);
        if (data.containsKey("sponsorId")) updateSponsor(userId, requireSponsor(data.get("sponsorId")));

        if (data.get("accountNumber") != null) {
            saveBankDetails(userId, str(data.get("bankName")), str(data.get("accountNumber")),
                    str(data.get("ifscCode")), str(data.get("accountName")));
        }

        if (data.get("institutionId") != null && data.get("courseId") != null) {
            saveStudentCourse(userId, str(data.get("institutionId")), toLong(data.get("courseId")));
        }
    }

    /** Fields kept on the student's application row (grantee_details): family details and the RCC center. */
    private static final List<String> FAMILY_FIELDS = List.of("fatherName", "motherName", "fatherProfession",
            "motherProfession", "fatherMobile", "motherMobile", "averageAnnualSalary", "address", "rccName");

    /**
     * Family details live on the student's application row (grantee_details). Students added without an
     * application (e.g. Add User) get one created the first time family details are entered.
     */
    private void updateFamilyDetails(Long userId, Map<String, Object> data) {
        if (FAMILY_FIELDS.stream().noneMatch(data::containsKey)) return;
        BigDecimal income = parseIncome(data.get("averageAnnualSalary"));

        List<GranteeDetails> rows = new ArrayList<>(granteeDetailsRepository.findByUserId(userId));
        if (rows.isEmpty()) {
            if (FAMILY_FIELDS.stream().allMatch(f -> isBlank(data.get(f)))) return; // nothing to store yet
            User user = requireUser(userRepository, userId, "Student not found");
            GranteeDetails gd = new GranteeDetails();
            gd.setUserId(userId);
            gd.setName(user.getName());
            gd.setStudentMobile(user.getPhone());
            rows.add(gd);
        }
        for (GranteeDetails gd : rows) {
            if (data.containsKey("fatherName")) gd.setFatherName(trimToNull(str(data.get("fatherName"))));
            if (data.containsKey("motherName")) gd.setMotherName(trimToNull(str(data.get("motherName"))));
            if (data.containsKey("fatherProfession")) gd.setFatherProfession(trimToNull(str(data.get("fatherProfession"))));
            if (data.containsKey("motherProfession")) gd.setMotherProfession(trimToNull(str(data.get("motherProfession"))));
            if (data.containsKey("fatherMobile")) gd.setFatherMobile(trimToNull(str(data.get("fatherMobile"))));
            if (data.containsKey("motherMobile")) gd.setMotherMobile(trimToNull(str(data.get("motherMobile"))));
            if (data.containsKey("averageAnnualSalary")) gd.setAverageAnnualSalary(income);
            if (data.containsKey("address")) gd.setAddress(trimToNull(str(data.get("address"))));
            if (data.containsKey("rccName")) gd.setRccName(trimToNull(str(data.get("rccName"))));
            granteeDetailsRepository.save(gd);
        }
    }

    /** Average annual family income: null when blank, 400 when not a non-negative number. */
    private static BigDecimal parseIncome(Object value) {
        if (isBlank(value)) return null;
        BigDecimal income;
        try {
            income = new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Average annual family income must be a number.");
        }
        if (income.signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Average annual family income cannot be negative.");
        return income;
    }

    /** The sponsor's users.id from a dropdown (null when none was chosen); 400 when it isn't a sponsor. */
    private Long requireSponsor(Object value) {
        Long sponsorId = toLong(value);
        if (sponsorId == null) return null;
        return userRepository.findById(sponsorId)
                .filter(s -> SPONSOR_ROLES.contains(s.getRoleId()))
                .map(User::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The selected sponsor does not exist."));
    }

    /** Maps the student to the sponsor, or removes the mapping when sponsorId is null. */
    private void updateSponsor(Long studentId, Long sponsorId) {
        if (sponsorId == null) {
            grantorGranteeRepository.deleteByGranteeId(studentId);
        } else {
            sponsorMappingService.map(studentId, sponsorId, "Accepted", false);
        }
    }

    private void saveBankDetails(Long userId, String bankName, String accountNumber, String ifscCode, String accountName) {
        BankDetails bank = bankDetailsRepository.findFirstByUserId(userId).orElseGet(() -> studentService.newBankDetails(userId));
        bank.setBankName(bankName);
        bank.setAccountNumber(accountNumber);
        bank.setIfscCode(ifscCode);
        bank.setAccountName(accountName);
        bankDetailsRepository.save(bank);
    }

    /** deactivate / activate / unmap (from sponsor). Users are never deleted: deactivate them instead. */
    @Transactional
    public void studentAction(Long userId, String action) {
        switch (action == null ? "" : action) {
            case "deactivate" -> setStatus(userId, "Inactive");
            case "activate" -> setStatus(userId, "Active");
            case "unmap" -> grantorGranteeRepository.deleteByGranteeId(userId);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid action");
        }
    }

    private void setStatus(Long userId, String status) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setStatus(status);
            userRepository.save(u);
        });
    }

    // --------------------------------------------------------------- bulk uploads

    /** Creates / updates students and their application details from a CSV; returns a row-by-row report. */
    public BulkUploadReport bulkUploadStudents(MultipartFile file) throws IOException {
        BulkUploadReport report = new BulkUploadReport();
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).setAllowMissingColumnNames(true)
                .build().parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            requireColumns(parser.getHeaderNames(), "studentreference", "studentname");
            for (CSVRecord row : parser) {
                int rowNumber = (int) row.getRecordNumber() + 1; // +1 for the header line
                report.row();
                Map<String, String> r = normalizeHeaders(row, parser.getHeaderNames());
                String uId = r.getOrDefault("studentreference", "").trim();
                try {
                    if (uId.isEmpty()) { report.skipped(rowNumber, null, "No Student Reference."); continue; }
                    String name = r.getOrDefault("studentname", "").trim();
                    if (name.isEmpty()) { report.skipped(rowNumber, uId, "No Student Name."); continue; }
                    String email = blankToNull(r.get("email"));
                    String phone = r.getOrDefault("mobilestudent", "").trim();

                    User existing = userRepository.findByUserId(uId).orElse(null);
                    User user = existing != null ? existing : newStudent(uId);
                    if (user.getSex() == null) user.setSex("M");
                    user.setName(name);
                    user.setEmail(email != null ? email : (existing != null ? existing.getEmail() : uId + "@rahbar.com"));
                    user.setPhone(phone);
                    userRepository.save(user);
                    if (existing == null) report.created(); else report.updated();

                    GranteeDetails gd = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(user.getId()).orElse(null);
                    if (gd == null) {
                        gd = new GranteeDetails();
                        gd.setUserId(user.getId());
                        gd.setRccName(r.get("rccnon-rcc"));
                        gd.setFatherMobile(r.get("mobile-1"));
                        gd.setMotherMobile(r.get("mobile-2"));
                        gd.setStudentMobile(phone);
                    }
                    gd.setName(name);
                    gd.setFatherName(r.get("fathername"));
                    gd.setAddress(r.get("address"));
                    gd.setCourseApplied(r.get("course(branch)"));
                    granteeDetailsRepository.save(gd);
                } catch (Exception rowEx) {
                    // Keep going with the next row, but tell the admin why this one failed.
                    report.failed(rowNumber, uId, rowEx);
                }
            }
        }
        return report;
    }

    /** 400 when the CSV doesn't have the columns the upload needs (wrong file or wrong template). */
    private static void requireColumns(List<String> headers, String... required) {
        Set<String> present = new HashSet<>();
        for (String h : headers) present.add(h.trim().toLowerCase().replace(" ", "").replace("_", ""));
        List<String> missing = new ArrayList<>();
        for (String r : required) if (!present.contains(r)) missing.add(r);
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is missing required column(s): " + String.join(", ", missing)
                    + ". Download the template from the upload window and use its column names.");
        }
    }

    /** A new student account with the given user code (users.id is generated on save). */
    private User newStudent(String code) {
        User user = new User();
        user.setUserId(code);
        user.setRoleId(STUDENT_ROLE);
        user.setStatus("Active");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setMustChangePassword(true);
        return user;
    }

    /**
     * Port of admin.bulk_upload_sponsors: merges sponsors by email / phone / name+chapter and maps
     * the "Student Assigned" IDs straight to the sponsor (there are no commitment references any more).
     * Columns: Sponsor Name, Sponsor Email, Sponsor Mobile1, Sponsor Chapter, Student Assigned, optional Sponsor ID.
     * Returns how many rows were processed.
     */
    public BulkUploadReport bulkUploadSponsors(MultipartFile file) throws IOException {
        byte[] raw = file.getBytes();
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.contains("\uFFFD")) text = new String(raw, StandardCharsets.ISO_8859_1); // latin-1 fallback, as in Flask
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        BulkUploadReport report = new BulkUploadReport();
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).setAllowMissingColumnNames(true)
                .build().parse(new StringReader(text))) {
            requireColumns(parser.getHeaderNames(), "sponsorname");
            for (CSVRecord row : parser) {
                int rowNumber = (int) row.getRecordNumber() + 1;
                report.row();
                String reference = null;
                try {
                    Map<String, String> r = new HashMap<>();
                    for (String h : parser.getHeaderNames()) {
                        String key = h.trim().toLowerCase().replace(" ", "").replace("_", "");
                        r.put(key, row.isSet(h) ? blankToNull(row.get(h)) : null);
                    }
                    String name = Objects.requireNonNullElse(r.get("sponsorname"), "");
                    String email = r.get("sponsoremail");
                    String mobile1 = r.get("sponsormobile1");
                    // "Sponsor Chapter" is matched to a chapter by name; an unknown name leaves the sponsor without one.
                    String chapterName = r.get("sponsorchapter");
                    Long chapterId = chapterName == null ? null
                            : chapterRepository.findByChapterNameIgnoreCase(chapterName.trim()).map(Chapter::getChapterId).orElse(null);
                    reference = !name.isEmpty() ? name : email != null ? email : mobile1;
                    if (name.isEmpty() && email == null && mobile1 == null) {
                        report.skipped(rowNumber, null, "Empty row (no name, email or mobile).");
                        continue;
                    }

                    Optional<User> existing = Optional.empty();
                    if (email != null) existing = userRepository.findFirstByEmailAndRoleIdIn(email, SPONSOR_ROLES);
                    if (existing.isEmpty() && mobile1 != null) existing = userRepository.findFirstByPhoneAndRoleIdIn(mobile1, SPONSOR_ROLES);
                    if (existing.isEmpty() && !name.isEmpty()) existing = userRepository.findFirstByNameAndChapterIdAndRoleIdIn(name, chapterId, SPONSOR_ROLES);

                    Long sponsorId;
                    if (existing.isPresent()) {
                        sponsorId = existing.get().getId();
                        report.updated();
                    } else {
                        // New sponsor: use the sheet's Sponsor ID (user code) if given, otherwise generate one.
                        String given = r.get("sponsorid");
                        String code = given != null ? given : "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                        User sponsor = new User();
                        sponsor.setUserId(code);
                        sponsor.setName(name);
                        sponsor.setEmail(email != null ? email : code + "@rahbar.com");
                        sponsor.setPhone(mobile1 != null ? mobile1 : "");
                        sponsor.setSex("M");
                        sponsor.setRoleId(5);
                        sponsor.setStatus("active");
                        sponsor.setChapterId(chapterId);
                        if (chapterName != null && chapterId == null) {
                            report.warning(rowNumber, reference, "Chapter '" + chapterName + "' does not exist, so the sponsor has no chapter.");
                        }
                        sponsor.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
                        sponsor.setMustChangePassword(true);
                        sponsorId = userRepository.save(sponsor).getId();
                        report.created();
                    }

                    String assigned = r.get("studentassigned");
                    if (assigned != null) {
                        for (String stuId : assigned.split(",")) {
                            String sid = stuId.trim();
                            if (sid.isEmpty()) continue;
                            Optional<User> student = userRepository.findByUserId(sid);
                            if (student.isEmpty()) {
                                report.warning(rowNumber, reference, "Student '" + sid + "' does not exist, so it was not mapped.");
                                continue;
                            }
                            sponsorMappingService.map(student.get().getId(), sponsorId, "Accepted", false);
                            report.mapped();
                        }
                    }
                } catch (Exception rowEx) {
                    // Keep going with the next row, but tell the admin why this one failed.
                    report.failed(rowNumber, reference, rowEx);
                }
            }
        }
        return report;
    }

    /** Header line of the CSV templates offered in the upload windows (matches what the uploads read). */
    public String csvTemplate(String kind) {
        return switch (kind) {
            case "students" -> "Student Reference,Student Name,Email,Mobile Student,Father Name,Address,Course (Branch),RCC Non-RCC,Mobile-1,Mobile-2\n"
                    + "STU-1001,Ayesha Khan,ayesha@example.com,9876543210,Imran Khan,Patna,B.Tech (CSE),RCC,9876500001,9876500002\n";
            case "sponsors" -> "Sponsor ID,Sponsor Name,Sponsor Email,Sponsor Mobile1,Sponsor Chapter,Student Assigned\n"
                    + ",Abdul Rahman,abdul@example.com,9800000001,Jeddah,\"STU-1001,STU-1002\"\n";
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "Unknown template.");
        };
    }

    /** Adds (or updates) one student with application, bank, course and sponsor details. Returns the user code. */
    public String manualAddStudent(Map<String, Object> body) {
        String uId = String.valueOf(body.get("userId")).trim();
        String name = String.valueOf(body.get("name"));
        String email = body.get("email") != null ? String.valueOf(body.get("email")) : uId + "@rahbar.com";

        // Check the optional sponsor (users.id from the dropdown) and income first so bad input doesn't leave a half-saved student.
        Long sponsorId = requireSponsor(body.get("sponsorId"));
        BigDecimal income = parseIncome(body.get("salary"));

        User user = userRepository.findByUserId(uId).orElseGet(() -> {
            User n = newStudent(uId);
            n.setSex(isBlank(body.get("sex")) ? "M" : str(body.get("sex")));
            return n;
        });
        user.setName(name);
        user.setEmail(email);
        user.setPhone(str(body.get("phone")));
        user.setYear(toInteger(body.get("year")));
        user.setChapterId(requireChapter(chapterRepository, body.get("chapterId"))); // optional
        Long id = userRepository.save(user).getId();

        GranteeDetails gd = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(id).orElse(null);
        if (gd == null) {
            gd = new GranteeDetails();
            gd.setUserId(id);
            gd.setName(name);
            gd.setFatherMobile(str(body.get("fatherMobile")));
            gd.setMotherMobile(str(body.get("motherMobile")));
            gd.setStudentMobile(str(body.get("phone")));
        }
        gd.setFatherName(str(body.get("fatherName")));
        gd.setMotherName(str(body.get("motherName")));
        gd.setFatherProfession(trimToNull(str(body.get("fatherProfession"))));
        gd.setMotherProfession(trimToNull(str(body.get("motherProfession"))));
        gd.setAverageAnnualSalary(income);
        if (!isBlank(body.get("alumnus"))) gd.setRahbarAlumnus(str(body.get("alumnus")));
        gd.setAddress(str(body.get("address")));
        gd.setCourseApplied(str(body.get("courseName")));
        gd.setRccName(trimToNull(str(body.get("rccName")))); // RCC center (optional)
        granteeDetailsRepository.save(gd);

        // Bank details (Flask: only when a bank name is given; account name = student name)
        if (!isBlank(body.get("bankName"))) {
            saveBankDetails(id, str(body.get("bankName")), str(body.get("accountNumber")), str(body.get("ifscCode")), name);
        }

        if (!isBlank(body.get("institutionId")) && !isBlank(body.get("courseId"))) {
            saveStudentCourse(id, str(body.get("institutionId")), toLong(body.get("courseId")));
        }

        if (sponsorId != null) {
            sponsorMappingService.map(id, sponsorId, "Accepted", false);
        }
        return uId;
    }

    public Map<String, Object> sponsorDetails(Long userId) {
        Map<String, Object> profile = Rows.pick(requireUser(userRepository, userId, "Sponsor not found"),
                "id", "user_id", "name", "email", "phone", "chapter_id", "chapter_name", "status");
        List<User> students = new ArrayList<>(userRepository.findGranteesOf(userId));
        students.sort(Comparator.comparing(User::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return Map.of("profile", profile,
                "students", Rows.pickAll(students, "id", "user_id", "name", "email", "phone", "status"));
    }

    public void updateSponsor(Long userId, Map<String, Object> data) {
        User user = requireUser(userRepository, userId, "Sponsor not found");
        boolean hidden = hidesDetailsOf(user); // the editor saw blanks: keep the real details
        if (data.get("name") != null) user.setName(str(data.get("name")));
        if (!hidden && data.get("email") != null) user.setEmail(str(data.get("email")));
        if (!hidden && data.get("phone") != null) user.setPhone(str(data.get("phone")));
        if (!hidden && data.containsKey("chapterId")) user.setChapterId(requireChapter(chapterRepository, data.get("chapterId")));
        userRepository.save(user);
    }

    // ------------------------------------------------------------------ payments

    /** Records a new payment (credited to the student's sponsor) or edits an existing one. Returns the message. */
    public String recordPayment(String actionType, Long paymentId, Long granteeId, BigDecimal amount,
                                String paymentDate, String status, MultipartFile receipt) {
        boolean create = "create".equals(actionType);
        Payment payment;
        if (create) {
            Long sponsorId = grantorGranteeRepository.findFirstByGranteeId(granteeId)
                    .map(GrantorGrantee::getGrantorId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                            "This student is not assigned to any sponsor. Map the student to a sponsor first."));
            payment = new Payment();
            payment.setGrantorId(sponsorId);
            payment.setGranteeId(granteeId);
        } else {
            if (paymentId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Payment id is required.");
            payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found."));
        }

        if (receipt != null && !receipt.isEmpty()) {
            String filename = "admin_pay_" + granteeId + "_" + System.currentTimeMillis() + "_"
                    + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
            payment.setReceiptUrl(fileStorageService.store(receipt, filename));
        }
        payment.setAmount(amount);
        payment.setPaymentDate(parseDateTime(paymentDate));
        payment.setStatus(status);
        paymentRepository.save(payment);
        if (create) {
            String text = "A payment of " + amount + " was recorded for you (status: " + status + ").";
            notificationService.notify(granteeId, "Payment recorded", text, NotificationService.PAYMENT, "/student/payments", false);
            String student = userRepository.findById(granteeId).map(User::getName).orElse("your student");
            notificationService.notify(payment.getGrantorId(), "Payment recorded",
                    "The office recorded a payment of " + amount + " for " + student + ".",
                    NotificationService.PAYMENT, "/sponsor/payments", false);
        }
        return create ? "Payment recorded and linked to the student's sponsor successfully." : "Payment updated successfully.";
    }

    // ------------------------------------------------------------------- helpers

    /** Trimmed text, or null when blank (unlike blankToNull, keeps values such as "None"). */
    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() || t.equalsIgnoreCase("nan") || t.equalsIgnoreCase("none") ? null : t;
    }

    private static Map<String, String> normalizeHeaders(CSVRecord row, List<String> headers) {
        Map<String, String> result = new HashMap<>();
        for (String h : headers) {
            String key = h.trim().toLowerCase().replace(" ", "");
            result.put(key, row.isSet(h) ? row.get(h) : "");
        }
        return result;
    }
}
