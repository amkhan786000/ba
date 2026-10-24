package com.rahbar.service;

import com.rahbar.entity.CoachingStudent;
import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.CoachingStudentRepository;
import com.rahbar.repository.ScholarshipApplicationRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Coaching Center > Ready for Scholarship: Class 10 coaching students, best results first, for the office to invite
 * to apply for the Rahbar scholarship (by a call, or an email when they have one). Their scholarship application is
 * found by their name and a mobile number, so the coaching -> scholarship journey can be counted.
 */
@Service
public class CoachingPipelineService {

    static final int SCHOLARSHIP_CLASS = 10;
    static final Set<String> INVITE_METHODS = Set.of("CALL", "EMAIL", "OTHER");

    private final CoachingStudentRepository students;
    private final ScholarshipApplicationRepository applications;
    private final UserRepository users;
    private final CoachingAccess access;
    private final CoachingService coaching;
    private final CoachingResults results;
    private final CoachingAttendanceService attendance;
    private final PublicService publicService;
    private final EmailService emailService;
    private final String applyUrl;

    public CoachingPipelineService(CoachingStudentRepository students, ScholarshipApplicationRepository applications, UserRepository users,
                                   CoachingAccess access, CoachingService coaching, CoachingResults results,
                                   CoachingAttendanceService attendance, PublicService publicService, EmailService emailService,
                                   @Value("${app.scholarship-apply-url:http://www.biharanjuman.com/projects/rahbar-diploma-paramedical-sponsorship/apply}") String applyUrl) {
        this.students = students;
        this.applications = applications;
        this.users = users;
        this.access = access;
        this.coaching = coaching;
        this.results = results;
        this.attendance = attendance;
        this.publicService = publicService;
        this.emailService = emailService;
        this.applyUrl = applyUrl;
    }

    /** Class 10 students (studying) of the user's centers, optionally one center. */
    List<CoachingStudent> classTen(Long centerId) {
        Set<Long> allowed = access.centerIds();
        return students.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()) && SCHOLARSHIP_CLASS == s.getClassLevel() && allowed.contains(s.getRccCenterId()))
                .filter(s -> centerId == null || centerId.equals(s.getRccCenterId()))
                .toList();
    }

    /** The list: each Class 10 student's exam average, attendance, contact, invite and application. */
    @Transactional
    public Map<String, Object> list(Long centerId) {
        if (centerId != null) access.requireVisible(centerId, "Coaching center");
        List<CoachingStudent> list = classTen(centerId);
        Map<Long, ScholarshipApplication> apps = link(list);
        Map<Long, CoachingResults.StudentClass> classes = new HashMap<>();
        list.forEach(s -> classes.put(s.getId(), new CoachingResults.StudentClass(s.getRccCenterId(), s.getClassLevel(), s.getAdmissionDate())));
        Map<Long, List<CoachingResults.ExamResult>> res = results.of(classes);
        LocalDate today = LocalDate.now();
        Map<Long, long[]> att = attendance.counts(list.stream().map(CoachingStudent::getId).toList(), today.minusDays(89), today);
        Map<Long, String> centerNames = access.names();
        Map<Long, String> inviters = new HashMap<>();
        list.stream().map(CoachingStudent::getInvitedBy).filter(Objects::nonNull).distinct()
                .forEach(id -> users.findById(id).ifPresent(u -> inviters.put(id, u.getName())));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingStudent s : list) {
            List<CoachingResults.ExamResult> r = res.getOrDefault(s.getId(), List.of());
            long[] a = att.getOrDefault(s.getId(), new long[3]);
            ScholarshipApplication app = apps.get(s.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("name", s.getName());
            row.put("father_name", s.getFatherName());
            row.put("center_name", centerNames.get(s.getRccCenterId()));
            row.put("guardian_mobile", s.getGuardianMobile());
            row.put("student_mobile", s.getStudentMobile());
            row.put("email", s.getEmail());
            row.put("average_percent", CoachingResults.average(r));
            row.put("exams_sat", r.stream().filter(x -> x.percent() != null).count());
            row.put("exams_held", r.size());
            row.put("attendance_percent", CoachingAttendanceService.percent(a[0], a[1]));
            row.put("invited_at", s.getInvitedAt());
            row.put("invite_method", s.getInviteMethod());
            row.put("invited_by", s.getInvitedBy() == null ? null : inviters.get(s.getInvitedBy()));
            row.put("application_id", app == null ? null : app.getId());
            row.put("application_status", app == null ? null : app.getStatus());
            row.put("stage", stage(s, app));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("average_percent"), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> out = new LinkedHashMap<>(coaching.centers());
        out.put("applications_open", publicService.isApplicationPeriodOpen());
        out.put("apply_url", applyUrl);
        out.put("rows", rows);
        out.put("funnel", funnel(rows));
        return out;
    }

    /** READY, INVITED, APPLIED or ACCEPTED (rejected applications count as APPLIED). */
    static String stage(CoachingStudent s, ScholarshipApplication app) {
        if (app != null) return ScholarshipApplication.ACCEPTED.equals(app.getStatus()) ? "ACCEPTED" : "APPLIED";
        return s.getInvitedAt() != null ? "INVITED" : "READY";
    }

    /** Class 10 students -> invited -> applied -> accepted (each stage counts everyone who got that far). */
    static List<Map<String, Object>> funnel(List<Map<String, Object>> rows) {
        long all = rows.size();
        long invited = rows.stream().filter(r -> !"READY".equals(r.get("stage"))).count();
        long applied = rows.stream().filter(r -> "APPLIED".equals(r.get("stage")) || "ACCEPTED".equals(r.get("stage"))).count();
        long accepted = rows.stream().filter(r -> "ACCEPTED".equals(r.get("stage"))).count();
        return List.of(Map.of("label", "Class 10 students", "value", all), Map.of("label", "Invited", "value", invited),
                Map.of("label", "Applied", "value", applied), Map.of("label", "Accepted", "value", accepted));
    }

    /**
     * Each student's scholarship application: the one linked already, or else one with the same name (ignoring case
     * and spaces) and one of the same mobile numbers (student's or parent's), which is then linked.
     */
    @Transactional
    public Map<Long, ScholarshipApplication> link(List<CoachingStudent> list) {
        Map<Long, ScholarshipApplication> out = new HashMap<>();
        if (list.isEmpty()) return out;
        List<ScholarshipApplication> all = applications.findAll();
        Map<Long, ScholarshipApplication> byId = new HashMap<>();
        all.forEach(a -> byId.put(a.getId(), a));
        Set<Long> taken = new HashSet<>();
        students.findAll().forEach(s -> { if (s.getApplicationId() != null) taken.add(s.getApplicationId()); });
        for (CoachingStudent s : list) {
            if (s.getApplicationId() != null) {
                ScholarshipApplication a = byId.get(s.getApplicationId());
                if (a != null) out.put(s.getId(), a);
                continue;
            }
            String name = norm(s.getName());
            Set<String> phones = new HashSet<>();
            for (String p : new String[]{s.getGuardianMobile(), s.getStudentMobile()}) if (phone(p) != null) phones.add(phone(p));
            if (name == null || phones.isEmpty()) continue;
            all.stream()
                    .filter(a -> !taken.contains(a.getId()) && name.equals(norm(a.getName()))
                            && (phones.contains(phone(a.getStudentMobile())) || phones.contains(phone(a.getFatherMobile())) || phones.contains(phone(a.getMotherMobile()))))
                    .max(Comparator.comparing(ScholarshipApplication::getId))
                    .ifPresent(a -> {
                        s.setApplicationId(a.getId());
                        students.save(s);
                        taken.add(a.getId());
                        out.put(s.getId(), a);
                    });
        }
        return out;
    }

    /** Marks a student as invited (by a call or otherwise), or clears it (method empty). */
    @Transactional
    public Map<String, Object> markInvited(Long id, String method) {
        CoachingStudent s = coaching.requireStudent(id);
        if (method == null || method.isBlank()) {
            s.setInvitedAt(null);
            s.setInvitedBy(null);
            s.setInviteMethod(null);
        } else {
            String m = method.trim().toUpperCase(Locale.ROOT);
            if (!INVITE_METHODS.contains(m)) throw CoachingService.bad("Invited by a call, an email or another way.");
            invited(s, m);
        }
        students.save(s);
        return Map.of("message", s.getInvitedAt() == null ? "Invite cleared." : s.getName() + " is marked as invited.");
    }

    /** Emails the invitation (the student needs an email address) and marks them invited. */
    @Transactional
    public Map<String, Object> emailInvite(Long id) {
        CoachingStudent s = coaching.requireStudent(id);
        if (s.getEmail() == null || s.getEmail().isBlank()) throw CoachingService.bad(s.getName() + " has no email address. Add it on Students, or call them.");
        String center = access.names().get(s.getRccCenterId());
        boolean sent = emailService.send(EmailType.COACHING_SCHOLARSHIP_INVITE, s.getEmail(),
                ServiceSupport.vars("name", s.getName(), "center", center == null ? "your coaching center" : center, "apply_url", applyUrl));
        if (!sent) throw new ApiException(HttpStatus.BAD_GATEWAY, "The email could not be sent. Try again later, or call them.");
        invited(s, "EMAIL");
        students.save(s);
        return Map.of("message", "Invitation emailed to " + s.getEmail() + ".");
    }

    private static void invited(CoachingStudent s, String method) {
        s.setInvitedAt(LocalDateTime.now());
        s.setInvitedBy(Access.current().getUser().getId());
        s.setInviteMethod(method);
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("[.\\s]+", " ").toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    /** The last 10 digits of a phone number, or null. */
    static String phone(String p) {
        if (p == null) return null;
        String d = p.replaceAll("[^0-9]", "");
        return d.length() < 10 ? null : d.substring(d.length() - 10);
    }
}
