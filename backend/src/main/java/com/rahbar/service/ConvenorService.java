package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.ServiceSupport.*;

/** Convenor (role 4) screens. A convenor works on the students and sponsors of their own chapter. */
@Service
public class ConvenorService {

    private static final String CHAPTER_NOT_SET = "Your chapter is not set. Please update your profile.";

    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final PaymentRepository paymentRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final FileStorageService fileStorageService;
    private final SponsorMappingService sponsorMappingService;
    private final NotificationService notificationService;
    private final ApplicationService applicationService;

    public ConvenorService(UserRepository userRepository, ChapterRepository chapterRepository,
                           GranteeDetailsRepository granteeDetailsRepository,
                           GrantorGranteeRepository grantorGranteeRepository,
                           ApplicationStatusRepository applicationStatusRepository,
                           PaymentRepository paymentRepository, BankDetailsRepository bankDetailsRepository,
                           StudentInstitutionCourseRepository studentCourseRepository,
                           StudentProgressRepository studentProgressRepository,
                           FileStorageService fileStorageService, SponsorMappingService sponsorMappingService,
                           NotificationService notificationService, ApplicationService applicationService) {
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.paymentRepository = paymentRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.fileStorageService = fileStorageService;
        this.sponsorMappingService = sponsorMappingService;
        this.notificationService = notificationService;
        this.applicationService = applicationService;
    }

    private User convenor(Long convenorId) {
        return requireUser(userRepository, convenorId, "User not found");
    }

    /** The convenor's chapter id; 400 when it isn't set. */
    private Long requireChapter(User convenor) {
        if (convenor.getChapterId() == null) throw new ApiException(HttpStatus.BAD_REQUEST, CHAPTER_NOT_SET);
        return convenor.getChapterId();
    }

    public Map<String, Object> dashboard(Long convenorId) {
        User convenor = convenor(convenorId);
        Long chapterId = requireChapter(convenor);

        List<Object[]> applicationRows = granteeDetailsRepository.findWithApplicantNameByChapter(chapterId);
        List<GrantorGrantee> grantorGrantee = grantorGranteeRepository.findByGrantorId(convenorId);

        // Each student is "paid" if they have a payment within the last year.
        LocalDateTime yearAgo = LocalDateTime.now().minusDays(365);
        List<Map<String, Object>> grantees = new ArrayList<>();
        for (GrantorGrantee gg : grantorGrantee) {
            userRepository.findById(gg.getGranteeId()).ifPresent(u -> {
                Map<String, Object> row = Rows.of(u);
                row.put("paymentStatus",
                        paymentRepository.existsByGranteeIdAndPaymentDateGreaterThanEqual(u.getId(), yearAgo) ? "paid" : "unpaid");
                grantees.add(row);
            });
        }

        // Chart data: latest application status of the chapter's applications, and sponsors per chapter.
        Map<Long, ApplicationStatus> latest = latestByApplication(applicationStatusRepository.findLatestPerApplication());
        List<GranteeDetails> applications = applicationRows.stream().map(r -> (GranteeDetails) r[0]).toList();
        List<Map<String, Object>> byStatus = countBy(applications, gd -> {
            ApplicationStatus s = latest.get(gd.getGranteeDetailId());
            return s == null || s.getStatus() == null ? "no status" : s.getStatus();
        });
        List<Map<String, Object>> byRegion = countBy(userRepository.findByRoleId(5), u -> chapterLabel(u.getChapterName()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("convenor", Rows.of(convenor));
        result.put("applications", withApplicantName(applicationRows));
        result.put("grantees", grantees);
        result.put("applicationsByStatus", byStatus);
        result.put("sponsorsByRegion", byRegion);
        result.put("sponsors", Rows.list(userRepository.findSponsorsWithApplicantsInChapter(chapterId)));
        result.put("grantorGrantee", Rows.list(grantorGrantee));
        return result;
    }

    private static List<Map<String, Object>> withApplicantName(List<Object[]> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = Rows.of(r[0]);
            row.put("applicant_name", r[1]);
            result.add(row);
        }
        return result;
    }

    /** Applications of the convenor's chapter, sorted by application_id, applicant_name, status or date_submitted. */
    public List<Map<String, Object>> applications(Long convenorId, String sortBy, String order) {
        Long chapterId = requireChapter(convenor(convenorId));
        String column = switch (sortBy == null ? "" : sortBy) {
            case "applicant_name" -> "applicant_name";
            case "status" -> "status";
            case "date_submitted" -> "created_at";
            default -> "grantee_detail_id";
        };
        List<Map<String, Object>> rows = new ArrayList<>(withApplicantName(granteeDetailsRepository.findWithApplicantNameByChapter(chapterId)));
        rows.sort(byColumn(column, "desc".equalsIgnoreCase(order)));
        return rows;
    }

    /** Adds a new status row to the application (the status history lives in application_status). */
    public void updateApplicationStatus(Long applicationId, String status, String comments) {
        applicationService.updateStatus(applicationId, status, comments);
    }

    public Map<String, Object> manageSponsors(Long convenorId, String sortBy, String order) {
        Long chapterId = requireChapter(convenor(convenorId));
        String column = List.of("user_id", "name", "email", "status").contains(sortBy) ? sortBy : "user_id";
        List<Map<String, Object>> sponsors = new ArrayList<>(Rows.list(userRepository.findByRoleIdAndChapterId(5, chapterId)));
        sponsors.sort(byColumn(column, "desc".equalsIgnoreCase(order)));
        Long unassigned = unassignedGrantorId(userRepository);
        return Map.of("sponsors", sponsors,
                "nonAssignedGrantees", unassigned == null ? List.of() : Rows.list(userRepository.findGranteesOf(unassigned)));
    }

    /** Sets a sponsor's status; an inactive sponsor's students go back to the unassigned grantor. */
    public void updateSponsorStatus(Long sponsorId, String status) {
        boolean deactivate = "Inactive".equalsIgnoreCase(status);
        Long unassigned = deactivate ? unassignedGrantorId(userRepository) : null;
        if (deactivate && unassigned == null) {
            throw new ApiException(HttpStatus.CONFLICT, "The default grantor (user " + UNASSIGNED_GRANTOR_CODE + ") does not exist.");
        }
        userRepository.findById(sponsorId).ifPresent(u -> {
            u.setStatus(status);
            userRepository.save(u);
        });
        if (deactivate) {
            List<GrantorGrantee> mappings = grantorGranteeRepository.findByGrantorId(sponsorId);
            mappings.forEach(gg -> gg.setGrantorId(unassigned));
            grantorGranteeRepository.saveAll(mappings);
        }
    }

    /** Moves each student's existing mapping to the sponsor. */
    public void mapStudents(Long sponsorId, List<Long> studentIds) {
        for (Long studentId : studentIds) {
            grantorGranteeRepository.findFirstByGranteeId(studentId).ifPresent(gg -> {
                boolean changed = !sponsorId.equals(gg.getGrantorId());
                gg.setGrantorId(sponsorId);
                grantorGranteeRepository.save(gg);
                if (changed) sponsorMappingService.notifyMapped(studentId, sponsorId);
            });
        }
    }

    public List<Map<String, Object>> studentProgress(Long convenorId, String granteeName, Double minMarks, Double maxMarks,
                                                     String startDate, String endDate, String sortBy) {
        Long chapterId = convenor(convenorId).getChapterId();
        LocalDateTime from = parseDateTime(startDate);
        LocalDateTime to = parseDateTime(endDate);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : studentProgressRepository.findWithGranteeNameByChapter(chapterId)) {
            StudentProgress sp = (StudentProgress) r[0];
            String name = (String) r[1];
            Double marks = marks(sp.getMarks());
            if (!isBlank(granteeName) && !like(name, granteeName)) continue;
            if (minMarks != null && (marks == null || marks < minMarks)) continue;
            if (maxMarks != null && (marks == null || marks > maxMarks)) continue;
            if (from != null && (sp.getCreatedAt() == null || sp.getCreatedAt().isBefore(from))) continue;
            if (to != null && (sp.getCreatedAt() == null || sp.getCreatedAt().isAfter(to))) continue;
            Map<String, Object> row = Rows.of(sp);
            row.put("grantee_name", name);
            rows.add(row);
        }
        if ("marks".equals(sortBy)) {
            // Numeric sort, highest marks first (rows without numeric marks last).
            rows.sort(Comparator.comparing((Map<String, Object> row) -> marks((String) row.get("marks")),
                    Comparator.nullsFirst(Comparator.<Double>naturalOrder())).reversed());
        }
        return rows; // already newest first
    }

    public void updateChapter(Long convenorId, Object chapterId) {
        User user = convenor(convenorId);
        user.setChapterId(ServiceSupport.requireChapter(chapterRepository, chapterId));
        userRepository.save(user);
    }

    public Map<String, Object> payments(Long convenorId) {
        User convenor = convenor(convenorId);
        List<Long> studentIds = grantorGranteeRepository.findByGrantorId(convenorId).stream()
                .map(GrantorGrantee::getGranteeId).toList();

        Map<String, Object> studentDataMap = new LinkedHashMap<>();
        List<Map<String, Object>> studentsForDropdown = new ArrayList<>();
        for (User s : userRepository.findAllById(studentIds)) {
            Long sid = s.getId();
            Map<String, Object> card = StudentService.studentCard(s);
            studentsForDropdown.add(card);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("grantee", card);
            entry.put("bankDetails", bankDetailsRepository.findFirstByUserId(sid).map(Rows::of).orElse(Map.of()));
            entry.put("courseInfo", Rows.first(studentCourseRepository.findCourseInfo(sid)));
            entry.put("paidRecords", Rows.list(
                    paymentRepository.findByGranteeIdAndStatusInOrderByPaymentDateAsc(sid, List.of("Paid", "pending"))));
            studentDataMap.put(String.valueOf(sid), entry);
        }

        List<Payment> past = paymentRepository.findByGrantorIdOrderByPaymentDateDesc(convenorId);
        Map<Long, String> names = userNames(userRepository, past.stream().map(Payment::getGranteeId).toList());
        List<Map<String, Object>> pastPayments = new ArrayList<>();
        for (Payment p : past) {
            if (!names.containsKey(p.getGranteeId())) continue;
            Map<String, Object> row = Rows.of(p);
            row.put("grantee_name", names.get(p.getGranteeId()));
            pastPayments.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("convenor", Rows.of(convenor));
        result.put("studentsForDropdown", studentsForDropdown);
        result.put("studentDataMap", studentDataMap);
        result.put("pastPayments", pastPayments);
        return result;
    }

    /** Records a payment by the convenor; it stays "pending" until approved. */
    public void recordPayment(Long convenorId, Long granteeId, BigDecimal amount, MultipartFile receipt) {
        // Prefixed so two receipts with the same original name don't overwrite each other.
        String filename = "convenor_pay_" + granteeId + "_" + System.currentTimeMillis() + "_"
                + fileStorageService.sanitizeFilename(receipt.getOriginalFilename());
        Payment payment = new Payment();
        payment.setGrantorId(convenorId);
        payment.setGranteeId(granteeId);
        payment.setAmount(amount);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setReceiptUrl(fileStorageService.store(receipt, filename));
        payment.setStatus("pending");
        paymentRepository.save(payment);
        notificationService.notify(granteeId, "Payment recorded",
                "A payment of " + amount + " was recorded for you and is awaiting approval.",
                NotificationService.PAYMENT, "/student/payments", false);
    }

    public void uploadFile(MultipartFile file) {
        fileStorageService.store(file, fileStorageService.sanitizeFilename(file.getOriginalFilename()));
    }
}
