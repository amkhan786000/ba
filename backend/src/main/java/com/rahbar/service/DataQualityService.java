package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.website.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * Admin > Data Quality: things to fix in the data, grouped like the admin menu (Students, Sponsors & Payments,
 * Community, Website, Administration). Each check has a count, up to 200 rows, its group and the admin screen where
 * it is fixed. Follow-ups count as late after {@link #WAITING_DAYS} days.
 */
@Service
public class DataQualityService {

    private static final int MAX_ROWS = 200;
    /** Sign-ups, messages and reviews still open after this many days are listed. */
    static final int WAITING_DAYS = 7;

    private final UserRepository userRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final StudentInstitutionCourseRepository courseAssignmentRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final PaymentRepository paymentRepository;
    private final ChapterRepository chapterRepository;
    private final RoleRepository roleRepository;
    private final RccCenterRepository rccCenterRepository;
    private final VolunteerApplicationRepository volunteerRepository;
    private final WebMessageRepository webMessages;
    private final WebMemberRepository webMembers;
    private final WebBlogPostRepository webPosts;
    private final WebStoryRepository webStories;
    private final WebProjectRepository webProjects;

    public DataQualityService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                              BankDetailsRepository bankDetailsRepository,
                              StudentInstitutionCourseRepository courseAssignmentRepository,
                              GranteeDetailsRepository granteeDetailsRepository, PaymentRepository paymentRepository,
                              ChapterRepository chapterRepository, RoleRepository roleRepository,
                              RccCenterRepository rccCenterRepository, VolunteerApplicationRepository volunteerRepository,
                              WebMessageRepository webMessages, WebMemberRepository webMembers, WebBlogPostRepository webPosts,
                              WebStoryRepository webStories, WebProjectRepository webProjects) {
        this.userRepository = userRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.courseAssignmentRepository = courseAssignmentRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.paymentRepository = paymentRepository;
        this.chapterRepository = chapterRepository;
        this.roleRepository = roleRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.volunteerRepository = volunteerRepository;
        this.webMessages = webMessages;
        this.webMembers = webMembers;
        this.webPosts = webPosts;
        this.webStories = webStories;
        this.webProjects = webProjects;
    }

    // groups, the same as the admin menu
    static final String STUDENTS = "students", SPONSORS = "sponsors", COMMUNITY = "community", WEBSITE = "website",
            ADMINISTRATION = "administration";

    /**
     * A coaching-center student the office has not given a reference ID yet: not (yet) a scholarship student, so
     * the sponsor / bank / course / chapter checks don't apply.
     */
    static boolean coachingOnly(User u) {
        return u.getRccCenterId() != null && (u.getUserId() == null || u.getUserId().isBlank());
    }

    public List<Map<String, Object>> checks() {
        if (com.rahbar.security.Access.chapterScoped() || com.rahbar.security.Access.rccScoped()) {
            throw com.rahbar.security.Access.forbidden("Data quality checks cover the whole organisation and aren't available for chapter or RCC roles.");
        }
        // Current (scholarship) students: active accounts that are still studying or on hold.
        List<User> students = userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && StudyStatus.inProgramme(u.getStudyStatus()))
                .filter(u -> !coachingOnly(u))
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
        checks.add(in(SPONSORS, "/admin/sponsorships", userCheck("students_without_sponsor", "Students without a sponsor",
                "Current students not mapped to any sponsor.", students, u -> !sponsored.contains(u.getId()))));
        checks.add(in(SPONSORS, "/admin/students", userCheck("students_without_bank", "Students without bank details",
                "Payments can't be sent to these students.", students, u -> !withBank.contains(u.getId()))));
        checks.add(in(STUDENTS, "/admin/students", userCheck("students_without_course", "Students without a course",
                "No institution / course assigned, so their payment schedule can't be worked out.", students,
                u -> !withCourse.contains(u.getId()))));
        checks.add(in(STUDENTS, "/admin/students", userCheck("students_without_chapter", "Students without a chapter",
                "They don't show on any chapter dashboard.", students, u -> u.getChapterId() == null)));
        checks.add(in(STUDENTS, "/admin/students", userCheck("students_without_email", "Students without a real email address",
                "They can't get emails (no address, or a placeholder ...@rahbar.com).", students,
                u -> !usableEmail(u.getEmail()))));
        // Duplicates are checked across every active student account, whatever the study status.
        checks.add(in(STUDENTS, "/admin/students", duplicateNames(userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus())).toList())));
        checks.add(in(SPONSORS, "/admin/users", userCheck("sponsors_without_email", "Sponsors without a real email address",
                "They can't get payment reminders or broadcast emails.", sponsors, u -> !usableEmail(u.getEmail()))));
        checks.add(in(SPONSORS, "/admin/sponsorships", userCheck("sponsors_without_students", "Sponsors without students",
                "Active sponsors with no student mapped to them.", sponsors, u -> !sponsorsWithStudents.contains(u.getId()))));

        List<Map<String, Object>> unlinked = new ArrayList<>();
        for (GranteeDetails g : granteeDetailsRepository.findAll()) {
            if (g.getUserId() != null) continue;
            unlinked.add(row("application_id", g.getGranteeDetailId(), "name", g.getName(),
                    "student_mobile", g.getStudentMobile(), "submitted_at", g.getCreatedAt()));
        }
        checks.add(in(STUDENTS, "/admin/applications", check("applications_without_account", "Applications without a student account",
                "Applications not linked to a student user.", unlinked)));

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
        checks.add(in(SPONSORS, "/admin/payment-records", check("payments_without_receipt", "Payments without a receipt",
                "No receipt was uploaded for these payments.", noReceipt)));

        List<Map<String, Object>> noLead = new ArrayList<>();
        for (Chapter c : chapterRepository.findAllByOrderByChapterNameAsc()) {
            if (Boolean.FALSE.equals(c.getActive()) || usableEmail(c.getLeadEmail())) continue;
            noLead.add(row("chapter_id", c.getChapterId(), "chapter", c.getChapterName(), "lead", c.getLeadName()));
        }
        checks.add(in(COMMUNITY, "/admin/chapters", check("chapters_without_lead_email", "Chapters without a lead email",
                "Active chapters that broadcasts to 'all chapter leads' can't reach.", noLead)));

        checks.addAll(coachingCenterChecks());
        checks.addAll(leadChecks());
        checks.addAll(followUpChecks(LocalDateTime.now().minusDays(WAITING_DAYS)));
        checks.addAll(websiteContentChecks());
        return checks;
    }

    // ---------------------------------------------------------------- coaching centers

    private List<Map<String, Object>> coachingCenterChecks() {
        Map<Long, String> centers = new HashMap<>();
        List<Map<String, Object>> noIncharge = new ArrayList<>();
        for (RccCenter c : rccCenterRepository.findAll()) {
            centers.put(c.getRccCenterId(), c.getCenterName());
            if (blank(c.getInchargeName()) || blank(c.getContactNumber())) {
                noIncharge.add(row("rcc_center_id", c.getRccCenterId(), "center", c.getCenterName(),
                        "in_charge", c.getInchargeName(), "contact_number", c.getContactNumber()));
            }
        }
        List<Map<String, Object>> noRef = new ArrayList<>();
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
            if ("Inactive".equalsIgnoreCase(u.getStatus()) || u.getMergedIntoId() != null || !coachingOnly(u)) continue;
            noRef.add(row("id", u.getId(), "name", u.getName(), "phone", u.getPhone(),
                    "center", centers.get(u.getRccCenterId()), "added", u.getCreatedAt()));
        }
        return List.of(
                in(STUDENTS, "/admin/rcc-students", check("rcc_students_without_reference", "Coaching center students without a reference ID",
                        "Added by a coaching center; the office gives them a reference ID after their application.", noRef)),
                in(COMMUNITY, "/admin/rcc-centers", check("rcc_centers_without_incharge", "RCC centers without an in-charge or contact number",
                        "The website shows the in-charge's name; the office needs a number to reach the center.", noIncharge)));
    }

    // ---------------------------------------------------------------- leads and access

    /** Chapter / RCC-scoped accounts must be linked to their chapter / center, or their screens stay empty. */
    private List<Map<String, Object>> leadChecks() {
        Map<Integer, Role> roles = new HashMap<>();
        roleRepository.findAll().forEach(r -> roles.put(r.getRoleId(), r));
        List<Map<String, Object>> noChapter = new ArrayList<>(), noCenter = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            Role r = roles.get(u.getRoleId());
            if (r == null || "Inactive".equalsIgnoreCase(u.getStatus())) continue;
            Map<String, Object> row = row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(), "role", r.getRoleName(),
                    "email", u.getEmail());
            if (Role.SCOPE_CHAPTER.equals(r.getScope()) && u.getChapterId() == null) noChapter.add(row);
            if (Role.SCOPE_RCC.equals(r.getScope()) && u.getRccCenterId() == null) noCenter.add(row);
        }
        return List.of(
                in(ADMINISTRATION, "/admin/users", check("chapter_leads_without_chapter", "Chapter leads without a chapter",
                        "Their role only covers their own chapter, but no chapter is set: they see nothing.", noChapter)),
                in(ADMINISTRATION, "/admin/users", check("rcc_coordinators_without_center", "RCC coordinators without an RCC center",
                        "Their role only covers their own RCC center, but no center is set: they see nothing.", noCenter)));
    }

    // ---------------------------------------------------------------- follow-ups waiting

    private List<Map<String, Object>> followUpChecks(LocalDateTime before) {
        List<Map<String, Object>> volunteers = new ArrayList<>();
        for (VolunteerApplication v : volunteerRepository.findAllByOrderByCreatedAtDesc()) {
            if (!"NEW".equals(v.getStatus()) || !older(v.getCreatedAt(), before)) continue;
            volunteers.add(row("volunteer_id", v.getVolunteerId(), "name", v.getFullName(), "email", v.getEmail(),
                    "phone", v.getPhone(), "offers", String.join(", ", VolunteerService.offers(v)), "signed_up", v.getCreatedAt()));
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (WebMessage m : webMessages.findAll()) {
            if (m.isHandled() || !older(m.getCreatedAt(), before)) continue;
            messages.add(row("id", m.getId(), "name", m.getName(), "email", m.getEmail(), "subject", m.getSubject(), "received", m.getCreatedAt()));
        }
        List<Map<String, Object>> signUps = new ArrayList<>();
        for (WebMember m : webMembers.findAll()) {
            if (m.isApproved() || !older(m.getCreatedAt(), before)) continue;
            signUps.add(row("id", m.getId(), "name", m.getFullName(), "country", m.getCurrentCountry(), "email", m.getEmail(), "signed_up", m.getCreatedAt()));
        }
        List<Map<String, Object>> posts = new ArrayList<>();
        for (WebBlogPost p : webPosts.findAll()) {
            if (p.isPublished() || !p.isReviewRequested() || !older(p.getUpdatedAt(), before)) continue;
            posts.add(row("id", p.getId(), "title", p.getTitle(), "author", p.getAuthor(), "waiting_since", p.getUpdatedAt()));
        }
        List<Map<String, Object>> stories = new ArrayList<>();
        for (WebStory st : webStories.findAll()) {
            if (st.isPublished() || !st.isReviewRequested() || !older(st.getUpdatedAt(), before)) continue;
            stories.add(row("id", st.getId(), "title", st.getTitle(), "waiting_since", st.getUpdatedAt()));
        }
        String days = WAITING_DAYS + " days";
        return List.of(
                in(COMMUNITY, "/admin/volunteers", check("volunteers_waiting", "Volunteers not contacted",
                        "Still 'New' after " + days + ".", volunteers)),
                in(WEBSITE, "/admin/website/messages", check("website_messages_waiting", "Website messages not handled",
                        "Contact-form messages still open after " + days + ".", messages)),
                in(WEBSITE, "/admin/website/directory", check("directory_signups_waiting", "Directory sign-ups waiting",
                        "Not approved (or hidden) " + days + " after signing up.", signUps)),
                in(WEBSITE, "/admin/website/blog", check("blog_posts_waiting_review", "Blog posts waiting for review",
                        "Ready for review for more than " + days + ".", posts)),
                in(WEBSITE, "/admin/website/stories", check("stories_waiting_review", "Success stories waiting for review",
                        "Ready for review for more than " + days + ".", stories)));
    }

    // ---------------------------------------------------------------- website content gaps

    /** Things biharanjuman.com shows half empty. */
    private List<Map<String, Object>> websiteContentChecks() {
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (Chapter c : chapterRepository.findAllByOrderByChapterNameAsc()) {
            if (Boolean.FALSE.equals(c.getActive()) || (!blank(c.getDescription()) && !blank(c.getLeadName()))) continue;
            chapters.add(row("chapter_id", c.getChapterId(), "chapter", c.getChapterName(),
                    "missing", blank(c.getDescription()) && blank(c.getLeadName()) ? "description and lead name"
                            : blank(c.getDescription()) ? "description" : "lead name"));
        }
        List<Map<String, Object>> projects = new ArrayList<>();
        for (WebProject p : webProjects.findAll()) {
            if (p.isActive() && blank(p.getImageUrl())) projects.add(row("id", p.getId(), "project", p.getName(), "category", p.getCategory()));
        }
        List<Map<String, Object>> posts = new ArrayList<>();
        for (WebBlogPost p : webPosts.findAll()) {
            if (p.isPublished() && blank(p.getSummary())) posts.add(row("id", p.getId(), "title", p.getTitle(), "published", p.getPublishedAt()));
        }
        return List.of(
                in(COMMUNITY, "/admin/chapters", check("chapters_without_website_details", "Chapters without a description or lead name",
                        "The website's chapter pages show these; they look empty without them.", chapters)),
                in(WEBSITE, "/admin/website/projects", check("projects_without_image", "Projects without an image",
                        "Active projects shown on the website without a picture.", projects)),
                in(WEBSITE, "/admin/website/blog", check("blog_posts_without_summary", "Published blog posts without a summary",
                        "The blog list shows a summary under each title.", posts)));
    }

    /** Tags a check with its menu group and the admin screen where it is fixed. */
    private static Map<String, Object> in(String group, String link, Map<String, Object> check) {
        check.put("group", group);
        check.put("link", link);
        return check;
    }

    private static boolean older(LocalDateTime when, LocalDateTime before) {
        return when != null && when.isBefore(before);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
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
