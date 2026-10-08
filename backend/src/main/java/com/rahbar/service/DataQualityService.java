package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

/**
 * Admin > Data Quality: things to fix in the data (students without a sponsor, bank details, course or chapter,
 * possible duplicates, sponsors without email or students, ...). Each check has a count and up to 200 rows.
 */
@Service
public class DataQualityService {

    private static final int MAX_ROWS = 200;

    private final UserRepository userRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final StudentInstitutionCourseRepository courseAssignmentRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final PaymentRepository paymentRepository;
    private final ChapterRepository chapterRepository;

    public DataQualityService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                              BankDetailsRepository bankDetailsRepository,
                              StudentInstitutionCourseRepository courseAssignmentRepository,
                              GranteeDetailsRepository granteeDetailsRepository, PaymentRepository paymentRepository,
                              ChapterRepository chapterRepository) {
        this.userRepository = userRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.courseAssignmentRepository = courseAssignmentRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.paymentRepository = paymentRepository;
        this.chapterRepository = chapterRepository;
    }

    public List<Map<String, Object>> checks() {
        // Current students: active accounts that are still studying or on hold.
        List<User> students = userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && StudyStatus.inProgramme(u.getStudyStatus()))
                .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<User> sponsors = userRepository.findByRoleId(5).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()))
                .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
        Set<Long> sponsored = new HashSet<>(), sponsorsWithStudents = new HashSet<>();
        for (GrantorGrantee gg : grantorGranteeRepository.findAll()) {
            if (gg.getGrantorId() != null && !gg.getGrantorId().equals(unassigned)) {
                sponsored.add(gg.getGranteeId());
                sponsorsWithStudents.add(gg.getGrantorId());
            }
        }
        Set<Long> withBank = new HashSet<>(), withCourse = new HashSet<>();
        bankDetailsRepository.findAll().forEach(b -> withBank.add(b.getUserId()));
        courseAssignmentRepository.findAll().forEach(s -> withCourse.add(s.getUserId()));

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(userCheck("students_without_sponsor", "Students without a sponsor",
                "Current students not mapped to any sponsor.", students, u -> !sponsored.contains(u.getId())));
        checks.add(userCheck("students_without_bank", "Students without bank details",
                "Payments can't be sent to these students.", students, u -> !withBank.contains(u.getId())));
        checks.add(userCheck("students_without_course", "Students without a course",
                "No institution / course assigned, so their payment schedule can't be worked out.", students,
                u -> !withCourse.contains(u.getId())));
        checks.add(userCheck("students_without_chapter", "Students without a chapter",
                "They don't show on any chapter dashboard.", students, u -> u.getChapterId() == null));
        checks.add(userCheck("students_without_email", "Students without a real email address",
                "They can't get emails (no address, or a placeholder ...@rahbar.com).", students,
                u -> !usableEmail(u.getEmail())));
        // Duplicates are checked across every active student account, whatever the study status.
        checks.add(duplicateNames(userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus())).toList()));
        checks.add(userCheck("sponsors_without_email", "Sponsors without a real email address",
                "They can't get payment reminders or broadcast emails.", sponsors, u -> !usableEmail(u.getEmail())));
        checks.add(userCheck("sponsors_without_students", "Sponsors without students",
                "Active sponsors with no student mapped to them.", sponsors, u -> !sponsorsWithStudents.contains(u.getId())));

        List<Map<String, Object>> unlinked = new ArrayList<>();
        for (GranteeDetails g : granteeDetailsRepository.findAll()) {
            if (g.getUserId() != null) continue;
            unlinked.add(row("application_id", g.getGranteeDetailId(), "name", g.getName(),
                    "student_mobile", g.getStudentMobile(), "submitted_at", g.getCreatedAt()));
        }
        checks.add(check("applications_without_account", "Applications without a student account",
                "Applications not linked to a student user.", unlinked));

        Map<Long, User> people = new HashMap<>();
        userRepository.findAll().forEach(u -> people.put(u.getId(), u));
        List<Map<String, Object>> noReceipt = new ArrayList<>();
        for (Payment p : paymentRepository.findAll()) {
            if (p.getReceiptUrl() != null && !p.getReceiptUrl().isBlank()) continue;
            User st = people.get(p.getGranteeId()), sp = people.get(p.getGrantorId());
            noReceipt.add(row("payment_id", p.getPaymentId(), "payment_date", p.getPaymentDate(),
                    "student", st == null ? null : st.getName() + " (" + st.getUserId() + ")",
                    "sponsor", sp == null ? null : sp.getName() + " (" + sp.getUserId() + ")",
                    "amount", p.getAmount(), "status", p.getStatus()));
        }
        checks.add(check("payments_without_receipt", "Payments without a receipt",
                "No receipt was uploaded for these payments.", noReceipt));

        List<Map<String, Object>> noLead = new ArrayList<>();
        for (Chapter c : chapterRepository.findAllByOrderByChapterNameAsc()) {
            if (Boolean.FALSE.equals(c.getActive()) || usableEmail(c.getLeadEmail())) continue;
            noLead.add(row("chapter_id", c.getChapterId(), "chapter", c.getChapterName(), "lead", c.getLeadName()));
        }
        checks.add(check("chapters_without_lead_email", "Chapters without a lead email",
                "Active chapters that broadcasts to 'all chapter leads' can't reach.", noLead));
        return checks;
    }

    private Map<String, Object> userCheck(String key, String title, String description, List<User> users, Predicate<User> problem) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            if (problem.test(u)) rows.add(row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(),
                    "role_id", u.getRoleId(), "chapter", u.getChapterName(), "status", u.getStatus()));
        }
        return check(key, title, description, rows);
    }

    /** Students sharing the same name (ignoring case and spaces): possibly entered twice. */
    private Map<String, Object> duplicateNames(List<User> students) {
        Map<String, List<User>> byName = new LinkedHashMap<>();
        for (User u : students) {
            if (u.getName() == null) continue;
            byName.computeIfAbsent(u.getName().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(u);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        byName.values().stream().filter(l -> l.size() > 1).forEach(l -> l.forEach(u ->
                rows.add(row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(), "phone", u.getPhone(),
                        "study_status", StudyStatus.label(u.getStudyStatus()),
                        "chapter", u.getChapterName(), "joined", u.getCreatedAt()))));
        return check("possible_duplicate_students", "Possible duplicate students",
                "Active student accounts with the same name; check whether they were entered twice.", rows);
    }

    private static Map<String, Object> check(String key, String title, String description, List<Map<String, Object>> rows) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("key", key);
        c.put("title", title);
        c.put("description", description);
        c.put("count", rows.size());
        c.put("rows", rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows);
        return c;
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) row.put((String) keyValues[i], keyValues[i + 1]);
        return row;
    }

    private static boolean usableEmail(String email) {
        return email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }
}
