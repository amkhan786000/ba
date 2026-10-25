package com.rahbar.service;

import com.rahbar.util.UserRoles;
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

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:}")
    private String siteUrl;

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
    private final StudentStatusHistoryRepository statusHistoryRepository;
    private final PaymentInstallmentService installmentService;
    /** Chapter / RCC-scoped users only see their own students (see security.StudentScope). */
    private final com.rahbar.security.StudentScope studentScope;
    private final com.rahbar.security.SponsorPrivacy sponsorPrivacy;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final TrustedDeviceService trustedDevices;

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
                        com.rahbar.security.SponsorPrivacy sponsorPrivacy,
                        StudentStatusHistoryRepository statusHistoryRepository,
                        PaymentInstallmentService installmentService,
                        com.rahbar.security.StudentScope studentScope, TrustedDeviceService trustedDevices) {
        this.trustedDevices = trustedDevices;
        this.studentScope = studentScope;
        this.installmentService = installmentService;
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
        this.statusHistoryRepository = statusHistoryRepository;
        this.sponsorPrivacy = sponsorPrivacy;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
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

    /** Roles besides the main one (several roles per person). */
    @org.springframework.beans.factory.annotation.Autowired
    private AdditionalRoleService additionalRoles;

    /** One page of users (with role_name) ordered by user_id (the code); every filter is optional. */
    public Map<String, Object> listUsers(int page, int size, String name, String email, Integer roleId, String status) {
        Map<Integer, String> roleNames = roleNames();
        org.springframework.data.domain.Page<User> result = userRepository.searchUsers(likePattern(name), likePattern(email), roleId,
                isBlank(status) ? null : status.trim().toLowerCase(Locale.ROOT),
                pageRequest(page, size, org.springframework.data.domain.Sort.by("userId")));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, Set<Integer>> rolesOf = UserRoles.byUser(result.getContent(), userRepository);
        Map<Long, String> centers = new HashMap<>();
        rccCenterRepository.findAll().forEach(c -> centers.put(c.getRccCenterId(), c.getCenterName()));
        Map<Long, LocalDateTime> lastSignIn = activityLogService.lastSignIns();
        // The Delete button: only the Super Admin, and only for a user with nothing linked to them.
        boolean superAdmin = com.rahbar.security.Access.isSuperAdmin();
        Map<Long, List<String>> blockers = superAdmin ? userDeletion.blockers(result.getContent()) : Map.of();
        for (User u : result.getContent()) {
            Map<String, Object> row = Rows.of(u);
            row.put("role_name", roleNames.get(u.getRoleId()));
            row.put("rcc_center_name", u.getRccCenterId() == null ? null : centers.get(u.getRccCenterId()));
            row.put("last_sign_in", lastSignIn.get(u.getId()));
            List<Integer> more = rolesOf.getOrDefault(u.getId(), Set.of()).stream().filter(r -> !r.equals(u.getRoleId())).sorted().toList();
            row.put("additional_role_ids", more);
            row.put("additional_role_names", more.stream().map(roleNames::get).filter(Objects::nonNull).toList());
            List<String> why = superAdmin ? blockers.getOrDefault(u.getId(), List.of()) : List.of("Only the Super Admin can delete users.");
            row.put("can_delete", why.isEmpty());
            row.put("delete_blockers", why);
            rows.add(row);
        }
        return pageBody(result, rows);
    }

    /** Each user's last sign-in, for Manage Users. */
    @org.springframework.beans.factory.annotation.Autowired
    private ActivityLogService activityLogService;

    /** One user (with role_name) for the edit page. */
    public Map<String, Object> getUser(Long userId) {
        User u = requireUser(userRepository, userId, "User not found");
        Map<String, Object> row = Rows.of(u);
        row.put("role_name", roleRepository.findById(u.getRoleId()).map(Role::getRoleName).orElse(null));
        row.put("additional_role_ids", additionalRoles.of(u.getId()));
        return row;
    }

    private Map<Integer, String> roleNames() {
        Map<Integer, String> names = new HashMap<>();
        roleRepository.findAll().forEach(r -> names.put(r.getRoleId(), r.getRoleName()));
        return names;
    }

    /**
     * Adds a user with a generated temporary password (they must choose their own at first sign-in) and emails it
     * to them (template "New account"; the email log hides the password). When it can't be emailed, the result
     * carries the temporary password so the admin can pass it on.
     */
    public Map<String, Object> createUser(Map<String, Object> body) {
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
        String password = PasswordPolicy.temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true); // a temporary password: the user picks their own at first sign-in
        if (Integer.valueOf(STUDENT_ROLE).equals(roleId)) {
            user.setYear(sessionYear(body.get("year")));
            user.setPaymentStartDate(parseDate(body.get("paymentStartDate"), "Payment start date"));
            user.setStudyStatus(StudyStatus.STUDYING);
        }
        userRepository.save(user);
        if (body.get("additionalRoleIds") instanceof Collection<?> more) additionalRoles.set(user, more);

        String email = user.getEmail();
        boolean usableEmail = email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
        String role = roleRepository.findById(roleId).map(Role::getRoleName).orElse("a user");
        boolean emailed = usableEmail && emailService.sendWithSecret(EmailType.ACCOUNT_CREATED, email,
                vars("name", user.getName(), "role", role, "sign_in_url", signInUrl(), "email", email,
                        "user_id", user.getUserId(), "password", password), password);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("emailed", emailed);
        if (emailed) {
            result.put("message", "User " + code + " was added and their sign-in details were emailed to " + email + ".");
        } else {
            result.put("message", usableEmail
                    ? "User " + code + " was added, but the email could not be sent. Give them this temporary password."
                    : "User " + code + " was added. They have no email address, so give them this temporary password.");
            result.put("temporaryPassword", password);
        }
        return result;
    }

    /** The site address for emails: the first allowed origin (e.g. https://rahbar.example.org). */
    private String signInUrl() {
        String first = siteUrl == null ? "" : siteUrl.split(",")[0].trim();
        return first.isEmpty() ? "the Rahbar website" : first + "/login";
    }

    /** Session year of a student: optional, four digits. */
    private static Integer sessionYear(Object v) {
        if (isBlank(v)) return null;
        Integer year;
        try {
            year = Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            year = null;
        }
        if (year == null || year < 1990 || year > 2100) throw new ApiException(HttpStatus.BAD_REQUEST, "Session year must be a year like 2025.");
        return year;
    }

    /** An optional yyyy-MM-dd date. */
    private static LocalDate parseDate(Object v, String label) {
        if (isBlank(v)) return null;
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " must be a date like 2025-07-01.");
        }
    }

    private String nextNumericUserId() {
        long max = userRepository.findAllUserIds().stream()
                .filter(id -> id != null && id.matches("\\d{1,15}"))
                .mapToLong(Long::parseLong).max().orElse(1000);
        return String.valueOf(max + 1);
    }

    public void updateUser(Long userId, Map<String, Object> body) {
        User user = requireUser(userRepository, userId, "User not found");
        // only a Super Admin may change a Super Admin (as main or additional role)
        if (UserRoles.has(user, Role.SUPER_ADMIN, userRepository)) requireMayAssignRole(Role.SUPER_ADMIN);
        boolean hidden = hidesDetailsOf(user); // a sponsor's details the editor isn't allowed to see stay as they are
        if (body.get("name") != null) user.setName(String.valueOf(body.get("name")));
        if (body.containsKey("careOf")) user.setCareOf(careOf(body.get("careOf")));
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
        if (body.get("additionalRoleIds") instanceof Collection<?> more) additionalRoles.set(user, more);
        else additionalRoles.dropMain(user); // a new main role that was an additional one isn't listed twice
    }

    /** True for a sponsor whose details the signed-in user may not see (they were blanked on their screen). */
    private boolean hidesDetailsOf(User user) {
        return sponsorPrivacy.hidesDetails() && UserRoles.has(user, 5, userRepository);
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
        user.setFailedAttempts(0);
        user.setLockedUntil(null); // also unlocks an account locked after too many wrong passwords
        userRepository.save(user);
        trustedDevices.forgetAll(user.getId()); // remembered browsers must use a code again

        String email = user.getEmail();
        boolean usableEmail = email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
        boolean emailed = usableEmail && emailService.sendWithSecret(EmailType.PASSWORD_RESET_BY_ADMIN, email,
                vars("name", user.getName(), "password", password), password);
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

    /** Payment configs (per year and college type), newest year first, with the name of who last changed each. */
    public Map<String, Object> systemConfiguration() {
        List<PaymentSchedule> schedules = paymentScheduleRepository.findAllByOrderByYearDesc();
        Map<Long, String> names = userNames(userRepository, schedules.stream().map(PaymentSchedule::getUpdatedBy).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PaymentSchedule s : schedules) {
            Map<String, Object> row = Rows.pick(s, "schedule_id", "amount", "year", "college_type", "frequency_months", "due_notice_days", "updated_at");
            row.put("updated_by_name", s.getUpdatedBy() == null ? null : names.get(s.getUpdatedBy()));
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schedules", rows);
        return result;
    }

    /**
     * Amount of each installment for students of this session year and college type (GOVT, PRIVATE or ANY: everyone
     * of the year without a config of their own), and how often one is due (every 3 or 4 months). Unpaid installments
     * of the year's students are rebuilt with the new values. Returns the college type saved.
     */
    public String saveSchedule(int year, String collegeType, BigDecimal amount, Integer frequencyMonths, Integer dueNoticeDays) {
        String type = collegeType == null || collegeType.isBlank() ? PaymentInstallmentService.ANY : collegeType.trim().toUpperCase(Locale.ROOT);
        if (!PaymentInstallmentService.COLLEGE_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "College type must be Govt, Private or Any college.");
        }
        if (amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount cannot be negative.");
        }
        int frequency = frequencyMonths == null ? 3 : frequencyMonths;
        if (frequency != 3 && frequency != 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Frequency must be every 3 or every 4 months.");
        }
        PaymentSchedule schedule = paymentScheduleRepository.findByYearAndCollegeType(year, type).orElseGet(PaymentSchedule::new);
        schedule.setYear(year);
        schedule.setCollegeType(type);
        schedule.setAmount(amount);
        schedule.setFrequencyMonths(frequency);
        int notice = dueNoticeDays == null ? PaymentInstallmentService.DEFAULT_NOTICE_DAYS : dueNoticeDays;
        if (notice < 0 || notice > 120) throw new ApiException(HttpStatus.BAD_REQUEST, "\"Show as due\" must be between 0 and 120 days before the due date.");
        schedule.setDueNoticeDays(notice);
        schedule.setStatus(1);
        paymentScheduleRepository.saveAndFlush(schedule);
        installmentService.syncYear(year);
        return type;
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

    /** Adds or edits a course; students on an edited course get their installments rebuilt (semesters may have changed). */
    public Course saveCourse(Course course) {
        Course saved = courseRepository.saveAndFlush(course);
        if (course.getCourseId() != null) {
            studentCourseRepository.findByCourseId(saved.getCourseId()).forEach(sic -> installmentService.sync(sic.getUserId()));
        }
        return saved;
    }

    /** Deletes a course no student is assigned to. */
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found."));
        long students = studentCourseRepository.countByCourseId(id);
        if (students > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "\"" + course.getCourseName() + "\" can't be deleted: " + students
                    + " student(s) are assigned to it. Assign them another course first.");
        }
        courseRepository.delete(course);
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

    public Institution getInstitution(String institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Institution not found."));
    }

    /** Edits an institution's name and contact details (its id stays the same). */
    public void updateInstitution(String institutionId, Map<String, Object> body) {
        Institution institution = getInstitution(institutionId);
        String name = trimToNull(str(body.get("institutionName")));
        if (name == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Institution name is required.");
        institution.setInstitutionName(name);
        institution.setAddress(str(body.get("address")));
        institution.setContactNumber(str(body.get("contactNumber")));
        institution.setEmail(str(body.get("email")));
        institutionRepository.save(institution);
    }

    /** Deletes an institution that has no courses and no students. */
    public void deleteInstitution(String institutionId) {
        Institution institution = getInstitution(institutionId);
        long courses = courseRepository.countByInstitutionId(institutionId);
        long students = studentCourseRepository.countByInstitutionId(institutionId);
        if (courses > 0 || students > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "\"" + institution.getInstitutionName() + "\" can't be deleted: it has "
                    + courses + " course(s) and " + students + " student(s). Delete its courses (and move its students) first.");
        }
        institutionRepository.delete(institution);
    }

    // ------------------------------------------------------------------ applications

    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.ScholarshipApplicationRepository scholarshipApplications;

    /** One page of scholarship applications (newest first); filters (name, status code, RCC) are optional. */
    public Map<String, Object> applications(int page, int size, String name, String status, String rcc) {
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        org.springframework.data.domain.Sort newest = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id");
        String code = isBlank(status) ? null : status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        org.springframework.data.domain.Page<com.rahbar.entity.ScholarshipApplication> result;
        if (scope.all()) {
            result = scholarshipApplications.search(likePattern(name), likePattern(rcc), code, pageRequest(page, size, newest));
        } else {
            // Scoped (chapter / RCC) users: only their area's applications, paged here.
            Map<Long, User> users = usersById();
            List<com.rahbar.entity.ScholarshipApplication> visible = scholarshipApplications.search(likePattern(name), likePattern(rcc), code,
                            org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE, newest)).getContent().stream()
                    .filter(a -> scope.includesApplication(a, users)).toList();
            org.springframework.data.domain.PageRequest req = pageRequest(page, size, newest);
            int from = (int) Math.min(req.getOffset(), visible.size());
            int to = Math.min(from + req.getPageSize(), visible.size());
            result = new org.springframework.data.domain.PageImpl<>(visible.subList(from, to), req, visible.size());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.rahbar.entity.ScholarshipApplication a : result.getContent()) {
            Map<String, Object> row = Rows.of(a);
            row.put("status_label", ApplicationService.label(a.getStatus()));
            rows.add(row);
        }
        Map<String, Object> body = pageBody(result, rows);
        body.put("statuses", ApplicationService.statusOptions());
        return body;
    }

    /** Users by users.id (for scope checks on many rows). */
    private Map<Long, User> usersById() {
        Map<Long, User> users = new HashMap<>();
        userRepository.findAll().forEach(u -> users.put(u.getId(), u));
        return users;
    }

    /** A sponsor the signed-in user may see (chapter scope: their chapter's sponsors), else 404. */
    private User requireVisibleSponsor(Long sponsorId) {
        User u = requireUser(userRepository, sponsorId, "Sponsor not found");
        if (!studentScope.current().includesSponsor(u)) throw new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found");
        return u;
    }

    /** Keeps the rows whose "id" (users.id) is a student the signed-in user may see. */
    private List<Map<String, Object>> visibleStudentRows(List<Map<String, Object>> rows) {
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        if (scope.all()) return rows;
        Map<Long, User> users = usersById();
        return rows.stream().filter(r -> scope.includesId(toLong(r.get("id")), users)).toList();
    }

    public void updateApplicationStatus(Long applicationId, String status, String comments) {
        applicationService.updateStatus(applicationId, status, comments);
    }

    // ------------------------------------------------------------------ manage students

    /** One page of students with their sponsor, institution and course, filtered by institution, course and text. */
    public Map<String, Object> manageStudents(String q, String institutionId, Long courseId, String sessionYear, Integer page, Integer size) {
        List<Map<String, Object>> visible = visibleStudentRows(userRepository.findStudentOverview());
        List<Map<String, Object>> rows = visible.stream()
                .filter(r -> StudentDirectorySearch.sessionYearMatches(r.get("year"), sessionYear))
                .filter(r -> institutionId == null || institutionId.isBlank() || institutionId.equals(String.valueOf(r.get("institution_id"))))
                .filter(r -> courseId == null || courseId.equals(toLong(r.get("course_id"))))
                .filter(r -> com.rahbar.util.Paging.matches(q, r.get("student_name"), r.get("student_email"), r.get("student_phone"),
                        r.get("chapter_name"), r.get("sponsor_name") == null ? "Unassigned" : r.get("sponsor_name"),
                        r.get("institution_name") == null ? "Unassigned" : r.get("institution_name"),
                        r.get("course_name") == null ? "Unassigned" : r.get("course_name")))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>(com.rahbar.util.Paging.of(rows, page, size));
        body.put("years", StudentDirectorySearch.years(visible));
        return body;
    }

    public void assignStudentCourse(Long userId, String institutionId, Long courseId) {
        studentScope.requireStudent(userId);
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
        installmentService.sync(userId);
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
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        Map<Long, User> users = scope.all() ? Map.of() : usersById();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : userRepository.findActiveSponsorships()) {
            if (!scope.all() && !scope.includesSponsor(users.get(toLong(r.get("id"))))) continue;
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
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        Map<String, Object> sponsor = Rows.pick(requireVisibleSponsor(sponsorId),
                "id", "user_id", "name", "care_of", "email", "chapter_id", "chapter_name");
        if (hideContactInfo) sponsor.remove("email");

        List<User> mapped = new ArrayList<>(userRepository.findGranteesOf(sponsorId).stream()
                .filter(u -> "active".equalsIgnoreCase(u.getStatus()) && scope.includes(u)).toList());
        mapped.sort(Comparator.comparing(User::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sponsor", sponsor);
        Map<Long, GrantorGrantee> mine = new HashMap<>();
        grantorGranteeRepository.findByGrantorId(sponsorId).forEach(g -> mine.put(g.getGranteeId(), g));
        Map<Long, String> chapters = sponsorMappingService.chapterNames();
        List<Map<String, Object>> mappedRows = Rows.pickAll(mapped, "id", "user_id", "name", "email", "phone", "chapter_id", "chapter_name");
        mappedRows.forEach(r -> r.putAll(SponsorMappingService.onBehalfFields(mine.get((Long) r.get("id")), chapters)));
        result.put("mappedStudents", mappedRows);
        List<Map<String, Object>> available = visibleStudentRows(userRepository.findStudentsAvailableFor(sponsorId));
        // what the sponsor would like: students who fit come first, with why (Edit Sponsor > Student preferences)
        User sponsorUser = requireVisibleSponsor(sponsorId);
        SponsorMatch.Wish wish = SponsorMatch.Wish.of(sponsorUser);
        result.put("preferences", wish.any() ? SponsorPreferenceService.summary(wish.collegeType(), wish.fatherProfessions(), wish.incomeMin(), wish.incomeMax()) : null);
        if (wish.any()) {
            Map<Long, User> users = usersById();
            Map<Long, GranteeDetails> families = new HashMap<>();
            granteeDetailsRepository.findAll().stream().filter(g -> g.getUserId() != null)
                    .sorted(Comparator.comparing(GranteeDetails::getGranteeDetailId)).forEach(g -> families.putIfAbsent(g.getUserId(), g));
            List<Map<String, Object>> withFit = new ArrayList<>();
            for (Map<String, Object> r : available) {
                Map<String, Object> row = new LinkedHashMap<>(r);
                User st = users.get(toLong(r.get("id")));
                if (st != null) row.put("match", SponsorMatch.fit(wish, st, families.get(st.getId())).toMap());
                withFit.add(row);
            }
            withFit.sort(Comparator.comparing((Map<String, Object> r) -> !(r.get("match") instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("fits")))));
            available = withFit;
        }
        result.put("availableStudents", available);
        return result;
    }

    public void mapStudentsToSponsor(Long sponsorId, List<Long> studentIds) {
        mapStudentsToSponsor(sponsorId, studentIds, null);
    }

    /** onBehalf: these students are sponsored on behalf of an anonymous donor (null: the sponsor's own). */
    public void mapStudentsToSponsor(Long sponsorId, List<Long> studentIds, com.rahbar.dto.OnBehalf onBehalf) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select at least one student.");
        }
        requireVisibleSponsor(sponsorId);
        for (Long studentId : studentIds) studentScope.requireStudent(studentId);
        if (onBehalf != null && onBehalf.donorChapterId() != null) requireChapter(chapterRepository, onBehalf.donorChapterId());
        for (Long studentId : studentIds) {
            sponsorMappingService.map(studentId, sponsorId, "Accepted", false, null, onBehalf, true);
        }
    }

    /** { onBehalf: true, donorChapterId, donorLabel } from a request (null when not on behalf). */
    public static com.rahbar.dto.OnBehalf onBehalf(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("onBehalf"))) return null;
        Object ch = body.get("donorChapterId");
        if (ch == null || String.valueOf(ch).isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the anonymous donor's chapter.");
        return new com.rahbar.dto.OnBehalf(Long.valueOf(String.valueOf(ch).trim()), body.get("donorLabel") == null ? null : String.valueOf(body.get("donorLabel")));
    }

    // ------------------------------------------------------------- student directory

    /** Student directory page (DataTables shape: recordsTotal, recordsFiltered, data). */
    /** One page of the directory: institution / course filtered in the query, the rest in {@link StudentDirectorySearch}. */
    public Map<String, Object> listStudents(int start, int length, String search, String institutionId, Long courseId,
                                            StudentDirectorySearch.Filters filters) {
        List<Map<String, Object>> visible = visibleStudentRows(userRepository.searchStudentDirectory(
                isBlank(institutionId) ? null : institutionId, courseId, null));
        List<Map<String, Object>> filtered = StudentDirectorySearch.apply(visible, search, filters);
        int from = Math.min(Math.max(start, 0), filtered.size());
        int to = Math.min(from + Math.max(length, 0), filtered.size());
        return Map.of("recordsTotal", studentScope.current().all() ? userRepository.countByRoleId(STUDENT_ROLE) : (long) filtered.size(),
                "recordsFiltered", (long) filtered.size(),
                "data", withDeleteInfo(filtered.subList(from, to)),
                "years", StudentDirectorySearch.years(visible));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private UserDeletionService userDeletion;

    /** Books & materials the student received (shown in their profile). */
    @org.springframework.beans.factory.annotation.Autowired
    private MaterialService materialService;

    /** Student Directory > Delete (Super Admin; see UserDeletionService#deleteStudent). */
    public void deleteStudent(Long userId) {
        studentScope.requireStudent(userId);
        userDeletion.deleteStudent(userId);
    }

    /**
     * The Delete button of each row: only the Super Admin may delete, and only a student with nothing but their user and
     * application / family details; delete_blockers says why not (shown on the disabled button).
     */
    private List<Map<String, Object>> withDeleteInfo(List<Map<String, Object>> page) {
        boolean superAdmin = com.rahbar.security.Access.isSuperAdmin();
        Map<Long, List<String>> blockers = superAdmin
                ? userDeletion.studentBlockers(page.stream().map(r -> toLong(r.get("id"))).toList()) : Map.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : page) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            List<String> why = superAdmin ? blockers.getOrDefault(toLong(r.get("id")), List.of()) : List.of("Only the Super Admin can delete students.");
            row.put("can_delete", why.isEmpty());
            row.put("delete_blockers", why);
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> studentDetails(Long userId) {
        User user = studentScope.requireStudent(userId);
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
        profile.put("study_status", StudyStatus.of(user.getStudyStatus()));
        profile.put("session_year", user.getYear());
        profile.put("payment_start_date", user.getPaymentStartDate());
        profile.put("college_type", user.getCollegeType());
        profile.put("self_financed", user.isSelfFinanced());
        profile.put("study_status_date", user.getStudyStatusDate());
        profile.put("study_status_note", user.getStudyStatusNote());
        profile.put("name", user.getName());

        List<Payment> payments = paymentRepository.findByGranteeIdOrderByPaymentDateDesc(userId);
        Map<Long, String> grantorNames = userNames(userRepository, payments.stream().map(Payment::getGrantorId).toList());
        List<Map<String, Object>> paymentRows = new ArrayList<>();
        for (Payment p : payments) {
            Map<String, Object> row = Rows.of(p);
            row.put("grantor_name", grantorNames.get(p.getGrantorId()));
            row.put("installment", installmentService.label(p.getPaymentId()));
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
        result.put("statusHistory", Rows.list(statusHistoryRepository.findByUserIdOrderByHistoryIdDesc(userId)));
        result.put("sponsorHistory", sponsorHistory(userId));
        result.put("materials", materialService.forScholar(userId));
        result.put("onBehalf", SponsorMappingService.onBehalfFields(grantorGranteeRepository.findFirstByGranteeId(userId).orElse(null),
                sponsorMappingService.chapterNames()));
        return result;
    }

    /** Partial update of a student's account, family details, bank details and course (only the fields sent). */
    public void updateStudent(Long userId, Map<String, Object> data) {
        studentScope.requireStudent(userId);
        userRepository.findById(userId).ifPresent(user -> {
            boolean changed = false;
            if (data.get("name") != null) { user.setName(str(data.get("name"))); changed = true; }
            if (data.get("refId") != null) { ServiceSupport.setRefId(userRepository, user, data.get("refId")); changed = true; }
            if (data.get("email") != null) { user.setEmail(str(data.get("email"))); changed = true; }
            if (data.get("phone") != null) { user.setPhone(str(data.get("phone"))); changed = true; }
            if (data.containsKey("chapterId")) { user.setChapterId(requireChapter(chapterRepository, data.get("chapterId"))); changed = true; }
            if (data.get("status") != null) { user.setStatus(str(data.get("status"))); changed = true; }
            if (data.containsKey("year")) { user.setYear(sessionYear(data.get("year"))); changed = true; }
            if (data.containsKey("paymentStartDate")) { user.setPaymentStartDate(parseDate(data.get("paymentStartDate"), "Payment start date")); changed = true; }
            if (data.containsKey("collegeType")) { user.setCollegeType(collegeType(data.get("collegeType"))); changed = true; }
            if (data.containsKey("selfFinanced")) { user.setSelfFinanced(yes(data.get("selfFinanced"))); changed = true; }
            if (changed) userRepository.save(user);
        });

        updateFamilyDetails(userId, data);
        if (data.containsKey("sponsorId")) {
            Long sponsorId = requireSponsor(data.get("sponsorId"));
            GrantorGrantee now = grantorGranteeRepository.findFirstByGranteeId(userId).orElse(null);
            Long current = now == null ? null : now.getGrantorId();
            boolean sendsOnBehalf = data.containsKey("onBehalf");
            com.rahbar.dto.OnBehalf onBehalf = sendsOnBehalf ? onBehalf(data) : null;
            boolean onBehalfChanged = sendsOnBehalf && sponsorId != null && Objects.equals(sponsorId, current) && !sameOnBehalf(now, onBehalf);
            if (!Objects.equals(sponsorId, current) || onBehalfChanged) {
                requireMayMap(); // the BA Details form always sends the sponsor; only a change needs the permission
                if (onBehalf != null) requireChapter(chapterRepository, onBehalf.donorChapterId());
                if (sponsorId == null) updateSponsor(userId, null, str(data.get("sponsorChangeReason")));
                else sponsorMappingService.map(userId, sponsorId, "Accepted", false, str(data.get("sponsorChangeReason")), onBehalf, sendsOnBehalf);
            }
        }

        if (data.get("accountNumber") != null) {
            saveBankDetails(userId, str(data.get("bankName")), str(data.get("accountNumber")),
                    str(data.get("ifscCode")), str(data.get("accountName")));
        }

        if (data.get("institutionId") != null && data.get("courseId") != null) {
            saveStudentCourse(userId, str(data.get("institutionId")), toLong(data.get("courseId")));
        }
        installmentService.sync(userId); // course, session year, college type or start date may have changed
    }

    /** Edit Sponsor > c/o: who brought the sponsor (at most 150 characters); empty clears it. */
    static String careOf(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.length() > 150) throw new ApiException(HttpStatus.BAD_REQUEST, "c/o can be at most 150 characters.");
        return s.isEmpty() ? null : s;
    }

    /** BA Details > Self financed: true / "YES" / "Y" is Yes; anything else (empty too) is No. */
    static boolean yes(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s);
    }

    /** BA Details > College type: GOVT, PRIVATE or ANY ("doesn't matter"); empty = not set yet. */
    private static String collegeType(Object value) {
        String t = value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        if (!PaymentInstallmentService.COLLEGE_TYPES.contains(t)) throw new ApiException(HttpStatus.BAD_REQUEST, "College type must be Govt, Private or Doesn't matter.");
        return t;
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
                .filter(s -> UserRoles.hasAny(s, SPONSOR_ROLES, userRepository))
                .map(User::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The selected sponsor does not exist."));
    }

    /**
     * Mapping a student to a sponsor (or removing it) needs Sponsorships: manage, wherever it is done (Sponsorships,
     * Student Directory BA Details, unmap, adding a student with a sponsor), not just Students: manage.
     */
    private static void requireMayMap() {
        if (!Access.can(com.rahbar.security.Section.SPONSORSHIPS, com.rahbar.security.Section.Level.EDIT)) {
            throw Access.forbidden("Mapping a student to a sponsor needs the Sponsorships (manage) permission.");
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.SponsorMappingHistoryRepository sponsorHistoryRepository;

    /**
     * Every sponsor the student has had, newest first: who (name and ID), from, until (empty: still sponsoring) and
     * why it ended when the office said so.
     */
    private List<Map<String, Object>> sponsorHistory(Long studentId) {
        List<com.rahbar.entity.SponsorMappingHistory> periods = sponsorHistoryRepository.findByGranteeIdOrderByStartedAtDesc(studentId);
        Map<Long, String> chapters = sponsorMappingService.chapterNames();
        Map<Long, User> sponsors = new HashMap<>();
        userRepository.findAllById(periods.stream().map(com.rahbar.entity.SponsorMappingHistory::getGrantorId).distinct().toList())
                .forEach(u -> sponsors.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.rahbar.entity.SponsorMappingHistory h : periods) {
            User sp = sponsors.get(h.getGrantorId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sponsor_id", h.getGrantorId());
            r.put("sponsor_code", sp == null ? null : sp.getUserId());
            r.put("sponsor_name", sp == null ? "(deleted sponsor)" : sp.getName());
            r.put("started_at", h.getStartedAt());
            r.put("ended_at", h.getEndedAt());
            r.put("end_reason", h.getEndReason());
            r.put("on_behalf_text", Boolean.TRUE.equals(h.getOnBehalf())
                    ? SponsorMappingService.onBehalfText(h.getDonorLabel(), chapters.get(h.getDonorChapterId())) : null);
            rows.add(r);
        }
        return rows;
    }

    private static boolean sameOnBehalf(GrantorGrantee gg, com.rahbar.dto.OnBehalf onBehalf) {
        boolean was = gg != null && Boolean.TRUE.equals(gg.getOnBehalf());
        if (!was && onBehalf == null) return true;
        if (was != (onBehalf != null)) return false;
        String label = onBehalf.label() == null || onBehalf.label().isBlank() ? null : onBehalf.label().strip();
        return Objects.equals(gg.getDonorChapterId(), onBehalf.donorChapterId()) && Objects.equals(gg.getDonorLabel(), label);
    }

    /** Maps the student to the sponsor, or removes the mapping when sponsorId is null. */
    private void updateSponsor(Long studentId, Long sponsorId, String reason) {
        if (sponsorId == null) {
            if (grantorGranteeRepository.findFirstByGranteeId(studentId).isPresent()) sponsorMappingService.unmap(studentId, reason);
        } else {
            sponsorMappingService.map(studentId, sponsorId, "Accepted", false, reason);
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

    /**
     * Sets a student's study status (Studying, On hold, Graduated, Dropped out) with the date it took effect and
     * an optional note, and keeps the change in the history. Separate from the account's Active / Inactive.
     */
    @Transactional
    public void setStudyStatus(Long userId, String status, String date, String note) {
        User user = studentScope.requireStudent(userId);
        if (!UserRoles.has(user, STUDENT_ROLE, userRepository)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only students have a study status.");
        }
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!StudyStatus.LABELS.containsKey(s)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown study status: " + status);
        LocalDate effective = isBlank(date) ? LocalDate.now() : LocalDate.parse(date.trim());
        String text = trimToNull(note);
        if (text != null && text.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "The note can be at most 500 characters.");

        boolean newlyGraduated = StudyStatus.GRADUATED.equals(s) && !StudyStatus.GRADUATED.equals(user.getStudyStatus());
        user.setStudyStatus(s);
        user.setStudyStatusDate(effective);
        user.setStudyStatusNote(text);
        userRepository.save(user);
        StudentStatusHistory h = new StudentStatusHistory();
        h.setUserId(userId);
        h.setStatus(s);
        h.setEffectiveDate(effective);
        h.setNote(text);
        statusHistoryRepository.save(h);
        installmentService.sync(userId); // graduated / dropped out: no more unpaid installments
        if (newlyGraduated) { // good news for the sponsor
            grantorGranteeRepository.findFirstByGranteeId(userId).ifPresent(gg -> notificationService.notify(gg.getGrantorId(),
                    user.getName() + " has graduated", user.getName() + " has completed their studies. Thank you for your support.",
                    NotificationService.MAPPING, "/sponsor/dashboard", EmailType.SPONSOR_STUDENT_GRADUATED,
                    vars("student_name", user.getName())));
        }
    }

    /** deactivate / activate / unmap (from sponsor). Users are never deleted: deactivate them instead. */
    @Transactional
    public void studentAction(Long userId, String action) {
        studentScope.requireStudent(userId);
        switch (action == null ? "" : action) {
            case "deactivate" -> setStatus(userId, "Inactive");
            case "activate" -> setStatus(userId, "Active");
            case "unmap" -> { requireMayMap(); sponsorMappingService.unmap(userId); }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid action");
        }
    }

    private void setStatus(Long userId, String status) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setStatus(status);
            userRepository.save(u);
        });
        installmentService.sync(userId);
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
                    // Optional columns: "Session Year" and "Payment Start Date" (yyyy-MM-dd).
                    if (!isBlank(r.get("sessionyear"))) user.setYear(sessionYear(r.get("sessionyear")));
                    if (!isBlank(r.get("paymentstartdate"))) user.setPaymentStartDate(parseDate(r.get("paymentstartdate"), "Payment Start Date"));
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
        user.setStudyStatus(StudyStatus.STUDYING);
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
            case "students" -> "Student Reference,Student Name,Email,Mobile Student,Father Name,Address,Course (Branch),RCC Non-RCC,Mobile-1,Mobile-2,Session Year,Payment Start Date\n"
                    + "STU-1001,Ayesha Khan,ayesha@example.com,9876543210,Imran Khan,Patna,B.Tech (CSE),RCC,9876500001,9876500002,2025,2025-08-01\n";
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
        if (sponsorId != null) requireMayMap();
        BigDecimal income = parseIncome(body.get("salary"));

        User user = userRepository.findByUserId(uId).orElseGet(() -> {
            User n = newStudent(uId);
            n.setSex(isBlank(body.get("sex")) ? "M" : str(body.get("sex")));
            return n;
        });
        user.setName(name);
        user.setEmail(email);
        user.setPhone(str(body.get("phone")));
        user.setYear(sessionYear(body.get("year")));
        user.setPaymentStartDate(parseDate(body.get("paymentStartDate"), "Payment start date"));
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
        installmentService.sync(id);
        return uId;
    }

    public Map<String, Object> sponsorDetails(Long userId) {
        Map<String, Object> profile = Rows.pick(requireVisibleSponsor(userId),
                "id", "user_id", "name", "care_of", "email", "phone", "chapter_id", "chapter_name", "status");
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        List<User> students = new ArrayList<>(userRepository.findGranteesOf(userId).stream().filter(scope::includes).toList());
        students.sort(Comparator.comparing(User::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        Map<Long, GrantorGrantee> mine = new HashMap<>();
        grantorGranteeRepository.findByGrantorId(userId).forEach(g -> mine.put(g.getGranteeId(), g));
        Map<Long, String> chapters = sponsorMappingService.chapterNames();
        List<Map<String, Object>> rows = Rows.pickAll(students, "id", "user_id", "name", "email", "phone", "status", "year", "chapter_name", "study_status");
        rows.forEach(r -> r.putAll(SponsorMappingService.onBehalfFields(mine.get((Long) r.get("id")), chapters)));
        profile.putAll(SponsorPreferenceService.of(requireVisibleSponsor(userId)));
        return Map.of("profile", profile, "students", rows);
    }

    public void updateSponsor(Long userId, Map<String, Object> data) {
        User user = requireVisibleSponsor(userId);
        boolean hidden = hidesDetailsOf(user); // the editor saw blanks: keep the real details
        if (data.get("name") != null) user.setName(str(data.get("name")));
        if (data.containsKey("careOf")) user.setCareOf(careOf(data.get("careOf")));
        if (!hidden && data.get("email") != null) user.setEmail(str(data.get("email")));
        if (!hidden && data.get("phone") != null) user.setPhone(str(data.get("phone")));
        if (!hidden && data.containsKey("chapterId")) user.setChapterId(requireChapter(chapterRepository, data.get("chapterId")));
        // what kind of student they'd like: { collegeType, fatherProfessions, incomeMin, incomeMax }
        if (data.get("preferences") instanceof Map<?, ?> prefs) {
            @SuppressWarnings("unchecked") Map<String, Object> body = (Map<String, Object>) prefs;
            SponsorPreferenceService.apply(user, SponsorPreferenceService.read(body));
        }
        userRepository.save(user);
    }

    // ------------------------------------------------------------------ payments

    /** Records a new payment (credited to the student's sponsor) or edits an existing one. Returns the message. */
    public String recordPayment(String actionType, Long paymentId, Long granteeId, BigDecimal amount,
                                String paymentDate, String status, MultipartFile receipt, Long installmentId) {
        boolean create = "create".equals(actionType);
        if (granteeId != null) studentScope.requireStudent(granteeId);
        if (create) installmentService.requireStudying(granteeId);
        if (create) installmentService.requireUnpaid(installmentId, granteeId, null);
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
        // The amount is typed from the bank receipt and kept, but never shown back; editing without one keeps it.
        if (amount != null) payment.setAmount(amount);
        else if (create) throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the amount paid.");
        payment.setPaymentDate(parseDateTime(paymentDate));
        payment.setStatus(status);
        paymentRepository.saveAndFlush(payment);
        installmentService.afterPayment(payment, create ? installmentId : null);
        if (create) {
            String which = installmentService.label(payment.getPaymentId());
            String text = "Your sponsor's " + which + " was recorded (status: " + status + ").";
            notificationService.notify(granteeId, "Payment recorded", text, NotificationService.PAYMENT, "/student/payments", false);
            String student = userRepository.findById(granteeId).map(User::getName).orElse("your student");
            notificationService.notify(payment.getGrantorId(), "Payment recorded",
                    "The office recorded " + which + " for " + student + ".",
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
