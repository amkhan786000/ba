package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
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
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public AdminService(UserRepository userRepository, RoleRepository roleRepository,
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
                        FileStorageService fileStorageService) {
        this.userRepository = userRepository;
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

    /** One page of users (with role_name) ordered by user_id; every filter is optional. */
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
    public Map<String, Object> getUser(String userId) {
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
        // Blank id: next number after the highest numeric user id (what the Add User form used to work out itself).
        user.setUserId(isBlank(body.get("userId")) ? nextNumericUserId() : String.valueOf(body.get("userId")).trim());
        user.setName(String.valueOf(body.get("name")));
        user.setEmail(String.valueOf(body.get("email")));
        user.setPhone(String.valueOf(body.get("contact")));
        user.setRoleId(Integer.valueOf(String.valueOf(body.get("roleId"))));
        user.setStatus(String.valueOf(body.getOrDefault("status", "Active")));
        user.setRegion(String.valueOf(body.getOrDefault("region", "Jeddah")));
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

    public void updateUser(String userId, Map<String, Object> body) {
        User user = requireUser(userRepository, userId, "User not found");
        if (body.get("name") != null) user.setName(String.valueOf(body.get("name")));
        if (body.get("email") != null) user.setEmail(String.valueOf(body.get("email")));
        if (body.get("roleId") != null) user.setRoleId(Integer.valueOf(String.valueOf(body.get("roleId"))));
        if (body.get("status") != null) user.setStatus(String.valueOf(body.get("status")));
        userRepository.save(user);
    }

    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    // --------------------------------------------------------- system configuration

    /** Payment amount per year, newest first, with the name of who last changed it. */
    public Map<String, Object> systemConfiguration() {
        List<PaymentSchedule> schedules = paymentScheduleRepository.findAllByOrderByYearDesc();
        Map<String, String> names = userNames(userRepository, schedules.stream().map(PaymentSchedule::getUpdatedBy).toList());
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

    public List<RccCenter> listRccCenters() {
        return rccCenterRepository.findAll();
    }

    public RccCenter saveRccCenter(RccCenter center) {
        RccCenter saved = rccCenterRepository.save(center);
        // Re-read so the response carries the stored created_at / created_by on edits too.
        return rccCenterRepository.findById(saved.getRccCenterId()).orElse(saved);
    }

    public void deleteRccCenter(Long id) {
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

    public void assignStudentCourse(String userId, String institutionId, Long courseId) {
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
    private void saveStudentCourse(String userId, String institutionId, Long courseId) {
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

    public Map<String, Object> sponsorMappingScreen(String sponsorId, boolean hideContactInfo) {
        Map<String, Object> sponsor = Rows.pick(requireUser(userRepository, sponsorId, "Sponsor not found"),
                "user_id", "name", "email", "region");
        if (hideContactInfo) sponsor.remove("email");

        List<User> mapped = new ArrayList<>(userRepository.findGranteesOf(sponsorId).stream()
                .filter(u -> "active".equalsIgnoreCase(u.getStatus())).toList());
        mapped.sort(Comparator.comparing(User::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sponsor", sponsor);
        result.put("mappedStudents", Rows.pickAll(mapped, "user_id", "name", "email", "phone", "region"));
        result.put("availableStudents", userRepository.findStudentsAvailableFor(sponsorId));
        return result;
    }

    public void mapStudentsToSponsor(String sponsorId, List<String> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select at least one student.");
        }
        for (String studentId : studentIds) {
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

    public Map<String, Object> studentDetails(String userId) {
        User user = requireUser(userRepository, userId, "Student not found");
        GranteeDetails gd = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(userId).orElse(null);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_id", user.getUserId());
        profile.put("user_real_name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("status", user.getStatus());
        profile.put("region", user.getRegion());
        profile.put("year", user.getYear());
        profile.putAll(gd != null ? Rows.of(gd) : Rows.empty(GranteeDetails.class));
        profile.put("user_id", userId);
        profile.put("name", user.getName());
        profile.put("annual_schedule_amount", user.getYear() == null ? 0 : paymentScheduleRepository
                .findFirstByYearAndStatus(user.getYear(), 1).map(s -> (Object) s.getAmount()).orElse(0));

        List<Payment> payments = paymentRepository.findByGranteeIdOrderByPaymentDateDesc(userId);
        Map<String, String> grantorNames = userNames(userRepository, payments.stream().map(Payment::getGrantorId).toList());
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
                .map(u -> Rows.pick(u, "user_id", "name", "email")).orElse(null));
        result.put("payments", paymentRows);
        result.put("documents", Rows.list(studentProgressRepository.findByGranteeIdOrderByCreatedAtDesc(userId)));
        return result;
    }

    /** Partial update of a student's account, family details, bank details and course (only the fields sent). */
    public void updateStudent(String userId, Map<String, Object> data) {
        userRepository.findById(userId).ifPresent(user -> {
            boolean changed = false;
            if (data.get("name") != null) { user.setName(str(data.get("name"))); changed = true; }
            if (data.get("email") != null) { user.setEmail(str(data.get("email"))); changed = true; }
            if (data.get("phone") != null) { user.setPhone(str(data.get("phone"))); changed = true; }
            if (data.get("region") != null) { user.setRegion(str(data.get("region"))); changed = true; }
            if (data.get("status") != null) { user.setStatus(str(data.get("status"))); changed = true; }
            if (changed) userRepository.save(user);
        });

        for (GranteeDetails gd : granteeDetailsRepository.findByUserId(userId)) {
            boolean changed = false;
            if (data.get("fatherName") != null) { gd.setFatherName(str(data.get("fatherName"))); changed = true; }
            if (data.get("motherName") != null) { gd.setMotherName(str(data.get("motherName"))); changed = true; }
            if (data.get("address") != null) { gd.setAddress(str(data.get("address"))); changed = true; }
            if (data.get("fatherMobile") != null) { gd.setFatherMobile(str(data.get("fatherMobile"))); changed = true; }
            if (data.get("motherMobile") != null) { gd.setMotherMobile(str(data.get("motherMobile"))); changed = true; }
            if (changed) granteeDetailsRepository.save(gd);
        }

        if (data.get("accountNumber") != null) {
            saveBankDetails(userId, str(data.get("bankName")), str(data.get("accountNumber")),
                    str(data.get("ifscCode")), str(data.get("accountName")));
        }

        if (data.get("institutionId") != null && data.get("courseId") != null) {
            saveStudentCourse(userId, str(data.get("institutionId")), toLong(data.get("courseId")));
        }
    }

    private void saveBankDetails(String userId, String bankName, String accountNumber, String ifscCode, String accountName) {
        BankDetails bank = bankDetailsRepository.findFirstByUserId(userId).orElseGet(() -> studentService.newBankDetails(userId));
        bank.setBankName(bankName);
        bank.setAccountNumber(accountNumber);
        bank.setIfscCode(ifscCode);
        bank.setAccountName(accountName);
        bankDetailsRepository.save(bank);
    }

    /** deactivate / activate / unmap (from sponsor) / delete (only when the student has no payments). */
    @Transactional
    public void studentAction(String userId, String action) {
        switch (action == null ? "" : action) {
            case "deactivate" -> setStatus(userId, "Inactive");
            case "activate" -> setStatus(userId, "Active");
            case "unmap" -> grantorGranteeRepository.deleteByGranteeId(userId);
            case "delete" -> {
                long payCount = paymentRepository.countByGranteeId(userId);
                if (payCount > 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Cannot delete student. " + payCount + " payment records exist. Deactivate instead.");
                }
                grantorGranteeRepository.deleteByGranteeId(userId);
                studentCourseRepository.deleteByUserId(userId);
                bankDetailsRepository.deleteByUserId(userId);
                granteeDetailsRepository.deleteByUserId(userId);
                userRepository.findById(userId).ifPresent(userRepository::delete);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid action");
        }
    }

    private void setStatus(String userId, String status) {
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

                    User existing = userRepository.findById(uId).orElse(null);
                    User user = existing != null ? existing : newStudent(uId);
                    if (user.getSex() == null) user.setSex("M");
                    user.setName(name);
                    user.setEmail(email != null ? email : (existing != null ? existing.getEmail() : uId + "@rahbar.com"));
                    user.setPhone(phone);
                    userRepository.save(user);
                    if (existing == null) report.created(); else report.updated();

                    GranteeDetails gd = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(uId).orElse(null);
                    if (gd == null) {
                        gd = new GranteeDetails();
                        gd.setUserId(uId);
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

    private User newStudent(String userId) {
        User user = new User();
        user.setUserId(userId);
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
                    String chapter = Objects.requireNonNullElse(r.get("sponsorchapter"), "General");
                    reference = !name.isEmpty() ? name : email != null ? email : mobile1;
                    if (name.isEmpty() && email == null && mobile1 == null) {
                        report.skipped(rowNumber, null, "Empty row (no name, email or mobile).");
                        continue;
                    }

                    Optional<User> existing = Optional.empty();
                    if (email != null) existing = userRepository.findFirstByEmailAndRoleIdIn(email, SPONSOR_ROLES);
                    if (existing.isEmpty() && mobile1 != null) existing = userRepository.findFirstByPhoneAndRoleIdIn(mobile1, SPONSOR_ROLES);
                    if (existing.isEmpty() && !name.isEmpty()) existing = userRepository.findFirstByNameAndRegionAndRoleIdIn(name, chapter, SPONSOR_ROLES);

                    String userId;
                    if (existing.isPresent()) {
                        userId = existing.get().getUserId();
                        report.updated();
                    } else {
                        // New sponsor: use the sheet's Sponsor ID if given, otherwise generate one.
                        String given = r.get("sponsorid");
                        userId = given != null ? given : "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                        User sponsor = new User();
                        sponsor.setUserId(userId);
                        sponsor.setName(name);
                        sponsor.setEmail(email != null ? email : userId + "@rahbar.com");
                        sponsor.setPhone(mobile1 != null ? mobile1 : "");
                        sponsor.setSex("M");
                        sponsor.setRoleId(5);
                        sponsor.setStatus("active");
                        sponsor.setRegion(chapter);
                        sponsor.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
                        sponsor.setMustChangePassword(true);
                        userRepository.save(sponsor);
                        report.created();
                    }

                    String assigned = r.get("studentassigned");
                    if (assigned != null) {
                        for (String stuId : assigned.split(",")) {
                            String sid = stuId.trim();
                            if (sid.isEmpty()) continue;
                            if (!userRepository.existsById(sid)) {
                                report.warning(rowNumber, reference, "Student '" + sid + "' does not exist, so it was not mapped.");
                                continue;
                            }
                            sponsorMappingService.map(sid, userId, "Accepted", false);
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

    /** Adds (or updates) one student with application, bank, course and sponsor details. Returns the user id. */
    public String manualAddStudent(Map<String, Object> body) {
        String uId = String.valueOf(body.get("userId"));
        String name = String.valueOf(body.get("name"));
        String email = body.get("email") != null ? String.valueOf(body.get("email")) : uId + "@rahbar.com";

        // Check the optional sponsor first so a bad sponsor id doesn't leave a half-saved student.
        String sponsorId = isBlank(body.get("sponsorId")) ? null : String.valueOf(body.get("sponsorId")).trim();
        if (sponsorId != null && !userRepository.existsByUserIdAndRoleIdIn(sponsorId, SPONSOR_ROLES)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "No sponsor with user ID '" + sponsorId + "' exists. Student " + uId + " was not saved.");
        }

        User user = userRepository.findById(uId).orElseGet(() -> {
            User n = newStudent(uId);
            n.setSex(isBlank(body.get("sex")) ? "M" : str(body.get("sex")));
            return n;
        });
        user.setName(name);
        user.setEmail(email);
        user.setPhone(str(body.get("phone")));
        user.setYear(toInteger(body.get("year")));
        userRepository.save(user);

        GranteeDetails gd = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(uId).orElse(null);
        if (gd == null) {
            gd = new GranteeDetails();
            gd.setUserId(uId);
            gd.setName(name);
            gd.setFatherMobile(str(body.get("fatherMobile")));
            gd.setMotherMobile(str(body.get("motherMobile")));
            gd.setStudentMobile(str(body.get("phone")));
        }
        gd.setFatherName(str(body.get("fatherName")));
        gd.setMotherName(str(body.get("motherName")));
        gd.setAddress(str(body.get("address")));
        gd.setCourseApplied(str(body.get("courseName")));
        gd.setRccName(str(body.get("rccName")));
        granteeDetailsRepository.save(gd);

        // Bank details (Flask: only when a bank name is given; account name = student name)
        if (!isBlank(body.get("bankName"))) {
            saveBankDetails(uId, str(body.get("bankName")), str(body.get("accountNumber")), str(body.get("ifscCode")), name);
        }

        if (!isBlank(body.get("institutionId")) && !isBlank(body.get("courseId"))) {
            saveStudentCourse(uId, str(body.get("institutionId")), toLong(body.get("courseId")));
        }

        if (sponsorId != null) {
            sponsorMappingService.map(uId, sponsorId, "Accepted", false);
        }
        return uId;
    }

    public Map<String, Object> sponsorDetails(String userId) {
        Map<String, Object> profile = Rows.pick(requireUser(userRepository, userId, "Sponsor not found"),
                "user_id", "name", "email", "phone", "region", "status");
        List<User> students = new ArrayList<>(userRepository.findGranteesOf(userId));
        students.sort(Comparator.comparing(User::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return Map.of("profile", profile,
                "students", Rows.pickAll(students, "user_id", "name", "email", "phone", "status"));
    }

    public void updateSponsor(String userId, Map<String, Object> data) {
        User user = requireUser(userRepository, userId, "Sponsor not found");
        if (data.get("name") != null) user.setName(str(data.get("name")));
        if (data.get("email") != null) user.setEmail(str(data.get("email")));
        if (data.get("phone") != null) user.setPhone(str(data.get("phone")));
        if (data.get("region") != null) user.setRegion(str(data.get("region")));
        userRepository.save(user);
    }

    // ------------------------------------------------------------------ payments

    /** Records a new payment (credited to the student's sponsor) or edits an existing one. Returns the message. */
    public String recordPayment(String actionType, Long paymentId, String granteeId, BigDecimal amount,
                                String paymentDate, String status, MultipartFile receipt) {
        boolean create = "create".equals(actionType);
        Payment payment;
        if (create) {
            String sponsorId = grantorGranteeRepository.findFirstByGranteeId(granteeId)
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
            notificationService.notify(payment.getGrantorId(), "Payment recorded",
                    "The office recorded a payment of " + amount + " for your student " + granteeId + ".",
                    NotificationService.PAYMENT, "/sponsor/payments", false);
        }
        return create ? "Payment recorded and linked to the student's sponsor successfully." : "Payment updated successfully.";
    }

    // ------------------------------------------------------------------- helpers

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
