package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.entity.WebBlogPost;
import com.rahbar.repository.WebBlogPostRepository;
import com.rahbar.entity.WebMember;
import com.rahbar.repository.WebMemberRepository;
import com.rahbar.entity.WebMessage;
import com.rahbar.repository.WebMessageRepository;
import com.rahbar.entity.WebProject;
import com.rahbar.repository.WebProjectRepository;
import com.rahbar.entity.WebStory;
import com.rahbar.repository.WebStoryRepository;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
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
    private final PaymentScheduleRepository paymentScheduleRepository;

    public DataQualityService(UserRepository userRepository, GrantorGranteeRepository grantorGranteeRepository,
                              BankDetailsRepository bankDetailsRepository,
                              StudentInstitutionCourseRepository courseAssignmentRepository,
                              GranteeDetailsRepository granteeDetailsRepository, PaymentRepository paymentRepository,
                              ChapterRepository chapterRepository, RoleRepository roleRepository,
                              RccCenterRepository rccCenterRepository, VolunteerApplicationRepository volunteerRepository,
                              WebMessageRepository webMessages, WebMemberRepository webMembers, WebBlogPostRepository webPosts,
                              WebStoryRepository webStories, WebProjectRepository webProjects, PaymentScheduleRepository paymentScheduleRepository) {
        this.paymentScheduleRepository = paymentScheduleRepository;
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

    /** The study statuses the student checks look at when the page doesn't choose: students who are studying now. */
    static final Set<String> DEFAULT_STUDY_STATUSES = Set.of(StudyStatus.STUDYING);

    /** Known study status codes from the page; nothing (or nothing known) means {@link #DEFAULT_STUDY_STATUSES}. */
    static Set<String> studyStatuses(Collection<String> requested) {
        Set<String> out = new LinkedHashSet<>();
        if (requested != null) {
            for (String r : requested) {
                if (r == null) continue;
                for (String part : r.split(",")) {
                    String code = part.trim().toUpperCase(Locale.ROOT);
                    if (StudyStatus.LABELS.containsKey(code)) out.add(code);
                }
            }
        }
        return out.isEmpty() ? DEFAULT_STUDY_STATUSES : out;
    }

    public List<Map<String, Object>> checks() {
        return checks(null);
    }

    /**
     * @param studyStatus the study statuses (Study tab) of the students the missing-data checks look at, e.g.
     *                    STUDYING and ON_HOLD; empty means studying only. Checks about the status itself (finished
     *                    students still mapped, possible duplicates) always look at everyone.
     */
    public List<Map<String, Object>> checks(Collection<String> studyStatus) {
        if (com.rahbar.security.Access.chapterScoped() || com.rahbar.security.Access.rccScoped()) {
            throw com.rahbar.security.Access.forbidden("Data quality checks cover the whole organisation and aren't available for chapter or RCC roles.");
        }
        Set<String> statuses = studyStatuses(studyStatus);
        // The scholarship students the checks are about: active accounts with one of the chosen study statuses.
        List<User> students = userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && statuses.contains(StudyStatus.of(u.getStudyStatus())))
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
        checks.add(byStudy(in(SPONSORS, "/admin/sponsorships", userCheck("students_without_sponsor", "Students without a sponsor",
                "Students not mapped to any sponsor.", students, u -> !sponsored.contains(u.getId())))));
        checks.add(byStudy(in(SPONSORS, "/admin/students", userCheck("students_without_bank", "Students without bank details",
                "Payments can't be sent to these students.", students, u -> !withBank.contains(u.getId())))));
        checks.add(byStudy(in(STUDENTS, "/admin/students", userCheck("students_without_course", "Students without a course",
                "No institution / course assigned, so their payment schedule can't be worked out.", students,
                u -> !withCourse.contains(u.getId())))));
        checks.add(byStudy(in(STUDENTS, "/admin/students", userCheck("students_without_chapter", "Students without a chapter",
                "They don't show on any chapter dashboard.", students, u -> u.getChapterId() == null))));
        checks.add(byStudy(in(STUDENTS, "/admin/students", userCheck("students_without_email", "Students without a real email address",
                "They can't get emails (no address, or a placeholder ...@rahbar.com).", students,
                u -> !usableEmail(u.getEmail())))));
        // Duplicates are checked across every active student account, whatever the study status.
        checks.add(in(STUDENTS, "/admin/students", duplicateStudents(userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null).toList())));
        checks.add(in(SPONSORS, "/admin/sponsorships", duplicateSponsors(sponsors)));
        checks.add(in(ADMINISTRATION, "/admin/users", samePersonTwoAccounts()));
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
            if (Boolean.FALSE.equals(c.getActive()) || c.leadEmails().stream().anyMatch(DataQualityService::usableEmail)) continue;
            noLead.add(row("chapter_id", c.getChapterId(), "chapter", c.getChapterName(), "lead", c.getLeadName()));
        }
        checks.add(in(COMMUNITY, "/admin/chapters", check("chapters_without_lead_email", "Chapters without a lead email",
                "Active chapters that broadcasts to 'all chapter leads' can't reach.", noLead)));

        checks.addAll(coachingCenterChecks());
        checks.addAll(paymentSetupChecks(students, sponsored, unassigned));
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

    // ---------------------------------------------------------------- payment set-up and records

    private static final java.util.regex.Pattern IFSC = java.util.regex.Pattern.compile("[A-Z]{4}0[A-Z0-9]{6}");

    /**
     * Things that stop installments or payments from working: missing session year / start date, years without a
     * payment config, finished students still mapped, inactive sponsors, bad bank details, wrong RCC names and
     * students' phone numbers that aren't Indian mobiles.
     */
    private List<Map<String, Object>> paymentSetupChecks(List<User> students, Set<Long> sponsored, Long unassigned) {
        Map<Long, User> users = new HashMap<>();
        userRepository.findAll().forEach(u -> users.put(u.getId(), u));

        List<Map<String, Object>> noYear = new ArrayList<>(), noStart = new ArrayList<>(), badPhone = new ArrayList<>();
        Map<Integer, Integer> studentsByYear = new TreeMap<>();
        for (User u : students) {
            if (sponsored.contains(u.getId()) && u.getYear() == null) {
                noYear.add(row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(), "chapter", u.getChapterName(),
                        "study_status", StudyStatus.label(u.getStudyStatus()), "payment_start_date", u.getPaymentStartDate()));
            }
            // every current scholarship student, sponsored or not: the first installment's due date
            if (u.getPaymentStartDate() == null) {
                noStart.add(row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(), "chapter", u.getChapterName(),
                        "study_status", StudyStatus.label(u.getStudyStatus()), "session_year", u.getYear(),
                        "sponsored", sponsored.contains(u.getId()) ? "Yes" : "No"));
            }
            if (u.getYear() != null) studentsByYear.merge(u.getYear(), 1, Integer::sum);
            String digits = u.getPhone() == null ? "" : u.getPhone().replaceAll("\\D", "");
            if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
            if (!blank(u.getPhone()) && !digits.matches("[6-9]\\d{9}")) {
                badPhone.add(row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(), "phone", u.getPhone(),
                        "chapter", u.getChapterName(), "study_status", StudyStatus.label(u.getStudyStatus())));
            }
        }

        List<Map<String, Object>> noConfig = new ArrayList<>();
        studentsByYear.forEach((year, count) -> {
            if (paymentScheduleRepository.findFirstByYearAndStatus(year, 1).isEmpty()) {
                noConfig.add(row("session_year", year, "current_students", count));
            }
        });

        Set<Long> current = new HashSet<>();
        students.forEach(u -> current.add(u.getId()));
        List<Map<String, Object>> finishedMapped = new ArrayList<>(), inactiveSponsor = new ArrayList<>();
        for (GrantorGrantee gg : grantorGranteeRepository.findAll()) {
            if (gg.getGrantorId() == null || gg.getGrantorId().equals(unassigned)) continue;
            User st = users.get(gg.getGranteeId()), sp = users.get(gg.getGrantorId());
            if (st == null || sp == null || "Inactive".equalsIgnoreCase(st.getStatus())) continue;
            String study = StudyStatus.of(st.getStudyStatus());
            Map<String, Object> r = row("id", st.getId(), "user_id", st.getUserId(), "name", st.getName(),
                    "study_status", StudyStatus.label(study), "sponsor", sp.getName() + " (" + ServiceSupport.refOf(sp) + ")");
            if (StudyStatus.GRADUATED.equals(study) || StudyStatus.DROPPED_OUT.equals(study)) finishedMapped.add(r);
            else if ("Inactive".equalsIgnoreCase(sp.getStatus()) && current.contains(st.getId())) inactiveSponsor.add(r);
        }

        List<Map<String, Object>> badBank = new ArrayList<>();
        for (BankDetails b : bankDetailsRepository.findAll()) {
            if (!current.contains(b.getUserId())) continue;
            String ifsc = b.getIfscCode() == null ? "" : b.getIfscCode().trim().toUpperCase(Locale.ROOT);
            String account = b.getAccountNumber() == null ? "" : b.getAccountNumber().replaceAll("\\s", "");
            String problem = account.isEmpty() ? "No account number" : !account.matches("\\d{9,18}") ? "Account number isn't 9-18 digits"
                    : ifsc.isEmpty() ? "No IFSC code" : !IFSC.matcher(ifsc).matches() ? "IFSC code isn't valid" : null;
            if (problem == null) continue;
            User u = users.get(b.getUserId());
            badBank.add(row("id", b.getUserId(), "user_id", u == null ? null : u.getUserId(), "name", u == null ? null : u.getName(),
                    "study_status", u == null ? null : StudyStatus.label(u.getStudyStatus()),
                    "bank", b.getBankName(), "ifsc_code", b.getIfscCode(), "problem", problem));
        }

        // RCC names that match no RCC center: the coaching center lead can't see these students.
        Set<String> centerNames = new HashSet<>();
        rccCenterRepository.findAll().forEach(c -> centerNames.add(com.rahbar.security.StudentScope.normalize(c.getCenterName())));
        List<Map<String, Object>> unknownRcc = new ArrayList<>();
        Set<Long> reported = new HashSet<>();
        for (GranteeDetails g : granteeDetailsRepository.findAll()) {
            if (g.getUserId() == null || blank(g.getRccName()) || !current.contains(g.getUserId()) || !reported.add(g.getUserId())) continue;
            String rcc = com.rahbar.security.StudentScope.normalize(g.getRccName());
            if (centerNames.contains(rcc) || rcc.equals("non-rcc") || rcc.equals("non rcc") || rcc.equals("rcc")) continue;
            User u = users.get(g.getUserId());
            unknownRcc.add(row("id", g.getUserId(), "user_id", u == null ? null : u.getUserId(), "name", g.getName(),
                    "study_status", u == null ? null : StudyStatus.label(u.getStudyStatus()), "rcc_name", g.getRccName()));
        }

        return List.of(
                byStudy(in(SPONSORS, "/admin/students", check("students_without_payment_start", "Students without a payment start date",
                        "No date for the first installment, so no fee schedule can be made once they are sponsored (Student Directory > BA Details).", noStart))),
                byStudy(in(SPONSORS, "/admin/students", check("sponsored_students_without_session_year", "Sponsored students without a session year",
                        "The session year picks their Payment Config (amount and frequency); no fee schedule until it is set (Student Directory > BA Details).", noYear))),
                byStudy(in(ADMINISTRATION, "/admin/system-configuration", check("session_years_without_payment_config", "Session years without a payment config",
                        "Students of these years get no installments until a Payment Config is added for the year.", noConfig))),
                in(SPONSORS, "/admin/sponsorships", check("finished_students_still_mapped", "Graduated or dropped-out students still mapped to a sponsor",
                        "The sponsor still sees them as current; unmap them so the sponsor can take a new student.", finishedMapped)),
                byStudy(in(SPONSORS, "/admin/sponsorships", check("students_with_inactive_sponsor", "Current students mapped to an inactive sponsor",
                        "Their sponsor's account is inactive, so nobody is paying for them.", inactiveSponsor))),
                byStudy(in(SPONSORS, "/admin/students", check("students_with_bad_bank_details", "Students with incomplete or invalid bank details",
                        "Missing account number, or an account number / IFSC code that doesn't look right (IFSC: 4 letters, 0, 6 letters or digits).", badBank))),
                byStudy(in(STUDENTS, "/admin/students", check("students_with_invalid_phone", "Students with an invalid phone number",
                        "Not a 10-digit Indian mobile number (starting 6-9), so OTPs and calls may not reach them.", badPhone))),
                byStudy(in(STUDENTS, "/admin/students", check("students_with_unknown_rcc", "Students whose RCC name matches no RCC center",
                        "Their coaching center lead can't see them. Correct the RCC name in Student Directory > BA Details.", unknownRcc))));
    }

    // ---------------------------------------------------------------- leads and access

    /** Chapter / RCC-scoped accounts must be linked to their chapter / center, or their screens stay empty. */
    private List<Map<String, Object>> leadChecks() {
        Map<Integer, Role> roles = new HashMap<>();
        roleRepository.findAll().forEach(r -> roles.put(r.getRoleId(), r));
        List<Map<String, Object>> noChapter = new ArrayList<>(), noCenter = new ArrayList<>();
        List<User> everyone = userRepository.findAll();
        Map<Long, java.util.Set<Integer>> rolesOf = UserRoles.byUser(everyone, userRepository);
        for (User u : everyone) {
            if ("Inactive".equalsIgnoreCase(u.getStatus())) continue;
            // any of their roles (main or additional) that covers only a chapter / center
            for (Integer roleId : rolesOf.getOrDefault(u.getId(), java.util.Set.of())) {
                Role r = roles.get(roleId);
                if (r == null) continue;
                Map<String, Object> row = row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(), "role", r.getRoleName(),
                        "email", u.getEmail());
                if (Role.SCOPE_CHAPTER.equals(r.getScope()) && u.getChapterId() == null && noChapter.stream().noneMatch(x -> u.getId().equals(x.get("id")))) noChapter.add(row);
                if (Role.SCOPE_RCC.equals(r.getScope()) && u.getRccCenterId() == null && noCenter.stream().noneMatch(x -> u.getId().equals(x.get("id")))) noCenter.add(row);
            }
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
                in(WEBSITE, "/admin/website/blog", check("blog_posts_waiting_review", "Knowledge Hub posts waiting for review",
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
                in(WEBSITE, "/admin/website/blog", check("blog_posts_without_summary", "Published Knowledge Hub posts without a summary",
                        "The Knowledge Hub lists show a summary under each title.", posts)));
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
            if (!problem.test(u)) continue;
            Map<String, Object> r = row("id", u.getId(), "user_id", u.getUserId(), "name", u.getName(),
                    "role_id", u.getRoleId(), "chapter", u.getChapterName(), "status", u.getStatus());
            if (Integer.valueOf(6).equals(u.getRoleId())) r.put("study_status", StudyStatus.label(u.getStudyStatus()));
            rows.add(r);
        }
        return check(key, title, description, rows);
    }

    /** Marks a check whose students follow the page's study status filter. */
    private static Map<String, Object> byStudy(Map<String, Object> check) {
        check.put("study_filtered", true);
        return check;
    }

    /**
     * Possible duplicate students. A matching name alone is not enough: two accounts are flagged when the name matches
     * and at least one of father's name, phone (account or application mobile) or email also matches, or when two of
     * those match even though the names differ (a misspelt name). Rows of one person share a "group".
     */
    private Map<String, Object> duplicateStudents(List<User> students) {
        Map<Long, GranteeDetails> applications = new HashMap<>();
        granteeDetailsRepository.findAll().stream()
                .filter(g -> g.getUserId() != null)
                .sorted(Comparator.comparing(GranteeDetails::getGranteeDetailId))
                .forEach(g -> applications.putIfAbsent(g.getUserId(), g));
        List<Person> people = new ArrayList<>();
        for (User u : students) {
            GranteeDetails g = applications.get(u.getId());
            Person p = new Person(u);
            p.add("father's name", text(g == null ? null : g.getFatherName()));
            p.add("phone", phone(u.getPhone()));
            p.add("phone", phone(g == null ? null : g.getStudentMobile()));
            p.add("email", email(u.getEmail()));
            people.add(p);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Duplicate d : duplicates(people)) {
            User u = d.person().user;
            GranteeDetails g = applications.get(u.getId());
            rows.add(row("group", d.group(), "id", u.getId(), "user_id", u.getUserId(), "name", u.getName(),
                    "father_name", g == null ? null : g.getFatherName(), "phone", u.getPhone(), "email", u.getEmail(),
                    "matched_on", d.matchedOn(), "study_status", StudyStatus.label(u.getStudyStatus()),
                    "chapter", u.getChapterName(), "joined", u.getCreatedAt()));
        }
        return check("possible_duplicate_students", "Possible duplicate students",
                "Active students with the same name and the same father's name, phone or email (or two of those with a "
                        + "different spelling of the name); check whether they were entered twice.", rows);
    }

    /**
     * The same person with two accounts in different roles (e.g. a staff account and a separate sponsor account), by
     * the same name. A person should be one account with several roles: the Super Admin merges them here. Groups of
     * student accounts only are left to "Possible duplicate students".
     */
    private Map<String, Object> samePersonTwoAccounts() {
        Map<Integer, String> roleNames = new HashMap<>();
        roleRepository.findAll().forEach(r -> roleNames.put(r.getRoleId(), r.getRoleName()));
        List<User> active = userRepository.findAll().stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null && text(u.getName()) != null).toList();
        Map<Long, Set<Integer>> rolesOf = UserRoles.byUser(active, userRepository);
        Map<String, List<User>> byName = new TreeMap<>();
        for (User u : active) byName.computeIfAbsent(text(u.getName()), k -> new ArrayList<>()).add(u);
        List<Map<String, Object>> rows = new ArrayList<>();
        int group = 0;
        for (List<User> same : byName.values()) {
            if (same.size() < 2) continue;
            boolean onlyStudents = same.stream().allMatch(u -> rolesOf.getOrDefault(u.getId(), Set.of()).equals(Set.of(ServiceSupport.STUDENT_ROLE)));
            long kinds = same.stream().map(u -> rolesOf.getOrDefault(u.getId(), Set.of())).distinct().count();
            if (onlyStudents || kinds < 2) continue;
            group++;
            for (User u : same) {
                List<String> names = rolesOf.getOrDefault(u.getId(), Set.of()).stream().map(r -> roleNames.getOrDefault(r, "role " + r)).toList();
                rows.add(row("group", group, "id", u.getId(), "user_id", u.getUserId(), "name", u.getName(),
                        "roles", String.join(", ", names), "chapter", u.getChapterName(), "joined", u.getCreatedAt()));
            }
        }
        return check("same_person_two_accounts", "Same person, two accounts?",
                "Accounts with the same name in different roles (e.g. a staff account and a sponsor account). If it is one "
                        + "person, the Super Admin merges them into one account that has both roles.", rows);
    }

    /** Possible duplicate sponsors: the same rule on full name, email, phone and chapter. */
    private Map<String, Object> duplicateSponsors(List<User> sponsors) {
        List<Person> people = new ArrayList<>();
        for (User u : sponsors) {
            Person p = new Person(u);
            p.add("email", email(u.getEmail()));
            p.add("phone", phone(u.getPhone()));
            p.add("chapter", u.getChapterId() == null ? null : String.valueOf(u.getChapterId()));
            people.add(p);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Duplicate d : duplicates(people)) {
            User u = d.person().user;
            rows.add(row("group", d.group(), "id", u.getId(), "user_id", u.getUserId(), "name", u.getName(),
                    "email", u.getEmail(), "phone", u.getPhone(), "chapter", u.getChapterName(),
                    "matched_on", d.matchedOn(), "joined", u.getCreatedAt()));
        }
        return check("possible_duplicate_sponsors", "Possible duplicate sponsors",
                "Active sponsors with the same name and the same email, phone or chapter (or two of those with a different "
                        + "spelling of the name); check whether they were entered twice.", rows);
    }

    /** A user and the values compared (field -> normalized values; a field can have several, e.g. two phones). */
    private static final class Person {
        final User user;
        final String name;
        final Map<String, Set<String>> values = new LinkedHashMap<>();

        Person(User user) {
            this.user = user;
            this.name = text(user.getName());
        }

        void add(String field, String value) {
            if (value != null) values.computeIfAbsent(field, k -> new LinkedHashSet<>()).add(value);
        }
    }

    private record Duplicate(int group, Person person, String matchedOn) {}

    /**
     * Pairs that match on name plus at least one other field, or on two other fields; grouped (a ~ b, b ~ c: one
     * group). Each person's "matched on" lists every field they share with someone in their group.
     */
    private static List<Duplicate> duplicates(List<Person> people) {
        // Candidate pairs: people sharing at least one value of any field (name included).
        Map<String, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < people.size(); i++) {
            Person p = people.get(i);
            if (p.name != null) buckets.computeIfAbsent("name\u0000" + p.name, k -> new ArrayList<>()).add(i);
            for (Map.Entry<String, Set<String>> e : p.values.entrySet()) {
                for (String v : e.getValue()) buckets.computeIfAbsent(e.getKey() + "\u0000" + v, k -> new ArrayList<>()).add(i);
            }
        }
        int[] parent = new int[people.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Map<Integer, Set<String>> matched = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (List<Integer> bucket : buckets.values()) {
            if (bucket.size() < 2 || bucket.size() > 50) continue; // a value shared by very many (e.g. a chapter) only counts with others
            for (int x = 0; x < bucket.size(); x++) {
                for (int y = x + 1; y < bucket.size(); y++) {
                    int a = bucket.get(x), b = bucket.get(y);
                    if (!seen.add((long) Math.min(a, b) * people.size() + Math.max(a, b))) continue;
                    List<String> same = sameFields(people.get(a), people.get(b));
                    if (same.size() < 2) continue; // name + one more, or two others
                    parent[find(parent, a)] = find(parent, b);
                    matched.computeIfAbsent(a, k -> new LinkedHashSet<>()).addAll(same);
                    matched.computeIfAbsent(b, k -> new LinkedHashSet<>()).addAll(same);
                }
            }
        }
        Map<Integer, Integer> groupNo = new LinkedHashMap<>();
        List<Duplicate> result = new ArrayList<>();
        List<Integer> flagged = new ArrayList<>(matched.keySet());
        flagged.sort(Comparator.comparing((Integer i) -> find(parent, i)).thenComparing(i -> people.get(i).name, Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i : flagged) {
            int group = groupNo.computeIfAbsent(find(parent, i), k -> groupNo.size() + 1);
            result.add(new Duplicate(group, people.get(i), String.join(", ", matched.get(i))));
        }
        result.sort(Comparator.comparingInt(Duplicate::group));
        return result;
    }

    /** The fields two people share: "name" and any other field with a value in common. */
    private static List<String> sameFields(Person a, Person b) {
        List<String> same = new ArrayList<>();
        if (a.name != null && a.name.equals(b.name)) same.add("name");
        for (Map.Entry<String, Set<String>> e : a.values.entrySet()) {
            Set<String> other = b.values.get(e.getKey());
            if (other != null && !Collections.disjoint(e.getValue(), other)) same.add(e.getKey());
        }
        return same;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i] = parent[parent[i]];
        return i;
    }

    /** Lower case, single spaces, no dots (so "Md. Imran" = "md imran"); null when blank. */
    private static String text(String s) {
        if (s == null) return null;
        String t = s.replace('.', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    /** The last 10 digits of a phone number (ignores +91, spaces, dashes); null when too short to be real. */
    private static String phone(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.length() < 7 ? null : d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /** A real email in lower case; null for blanks and generated @rahbar.com placeholders. */
    private static String email(String s) {
        return usableEmail(s) ? s.trim().toLowerCase(Locale.ROOT) : null;
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
