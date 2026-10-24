package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.security.StudentScope;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.FormInput.*;

/**
 * Students > Careers: job / internship openings (students and graduates say they're interested in their portal),
 * placements (recorded by the office, or added by the student and confirmed by the office; a confirmed one updates a
 * graduate's alumni "what they do now"), employers, and career guidance requests.
 */
@Service
public class CareerService {

    static final Set<String> KINDS = Set.of("JOB", "INTERNSHIP", "TRAINING");
    static final Set<String> INTEREST_STATUSES = Set.of("INTERESTED", "APPLIED", "SELECTED", "NOT_SELECTED");
    static final Set<String> PLACEMENT_KINDS = Set.of("JOB", "INTERNSHIP", "HIGHER_STUDIES", "SELF_EMPLOYED");
    static final Set<String> TOPICS = Set.of("COURSE", "JOBS", "HIGHER_STUDIES", "EXAMS", "OTHER");
    static final Set<String> GUIDANCE_STATUSES = Set.of("OPEN", "SCHEDULED", "DONE", "CLOSED");
    /** Office roles told about new guidance requests and placements to confirm. */
    static final List<Integer> OFFICE_ROLES = List.of(2, 8);

    private final CareerOpportunityRepository opportunities;
    private final CareerInterestRepository interests;
    private final PlacementRepository placements;
    private final EmployerRepository employers;
    private final GuidanceRequestRepository guidance;
    private final AlumniProfileRepository alumni;
    private final UserRepository users;
    private final StudentScope studentScope;
    private final NotificationService notifications;

    public CareerService(CareerOpportunityRepository opportunities, CareerInterestRepository interests, PlacementRepository placements,
                         EmployerRepository employers, GuidanceRequestRepository guidance, AlumniProfileRepository alumni,
                         UserRepository users, StudentScope studentScope, NotificationService notifications) {
        this.opportunities = opportunities;
        this.interests = interests;
        this.placements = placements;
        this.employers = employers;
        this.guidance = guidance;
        this.alumni = alumni;
        this.users = users;
        this.studentScope = studentScope;
        this.notifications = notifications;
    }

    // ================================================================ office: openings

    /** Every opening, newest first, with how many are interested (only students the user may see are counted). */
    public List<Map<String, Object>> opportunities() {
        Map<Long, Long> counts = new HashMap<>();
        Set<Long> visible = visibleStudentIds();
        for (CareerInterest i : interests.findAll()) if (visible.contains(i.getUserId())) counts.merge(i.getOpportunityId(), 1L, Long::sum);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CareerOpportunity o : opportunities.findAllByOrderByIdDesc()) {
            Map<String, Object> r = Rows.of(o);
            r.put("interested", counts.getOrDefault(o.getId(), 0L));
            r.put("open_now", isOpen(o));
            rows.add(r);
        }
        return rows;
    }

    /** Who is interested in an opening (students the user may see). */
    public List<Map<String, Object>> interested(Long opportunityId) {
        requireOpportunity(opportunityId);
        Set<Long> visible = visibleStudentIds();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CareerInterest i : interests.findByOpportunityId(opportunityId)) {
            if (!visible.contains(i.getUserId())) continue;
            users.findById(i.getUserId()).ifPresent(u -> {
                Map<String, Object> r = Rows.of(i);
                r.put("name", u.getName());
                r.put("code", u.getUserId());
                r.put("chapter_name", u.getChapterName());
                r.put("phone", u.getPhone());
                r.put("email", u.getEmail());
                r.put("study_status", StudyStatus.label(u.getStudyStatus()));
                rows.add(r);
            });
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveOpportunity(Long id, Map<String, Object> b) {
        CareerOpportunity o = id == null ? new CareerOpportunity() : requireOpportunity(id);
        o.setKind(code(b.get("kind"), KINDS, "JOB", "Kind"));
        o.setTitle(text(b.get("title"), "Title", 200, true));
        o.setOrganisation(text(b.get("organisation"), "Organisation", 200, true));
        o.setEmployerId(ServiceSupport.toLong(b.get("employerId")));
        if (o.getEmployerId() != null && employers.findById(o.getEmployerId()).isEmpty()) throw bad("Choose the employer from the list.");
        o.setLocation(text(b.get("location"), "Location", 150, false));
        o.setDescription(text(b.get("description"), "Description", 3000, false));
        o.setEligibility(text(b.get("eligibility"), "Who can apply", 500, false));
        o.setApplyLink(link(b.get("applyLink"), "Apply link"));
        o.setLastDate(date(b.get("lastDate"), "Apply by"));
        o.setStatus(code(b.get("status"), Set.of("OPEN", "CLOSED"), "OPEN", "Status"));
        opportunities.save(o);
        return Map.of("id", o.getId(), "message", "Opening saved.");
    }

    @Transactional
    public void deleteOpportunity(Long id) {
        CareerOpportunity o = requireOpportunity(id);
        interests.findByOpportunityId(id).forEach(interests::delete);
        opportunities.delete(o);
    }

    /** How an interested student's application went: { status, note }. */
    @Transactional
    public Map<String, Object> updateInterest(Long interestId, Map<String, Object> b) {
        CareerInterest i = interests.findById(interestId).orElseThrow(() -> notFound("Interest"));
        requireVisibleStudent(i.getUserId());
        i.setStatus(code(b.get("status"), INTEREST_STATUSES, null, "Status"));
        i.setNote(text(b.get("note"), "Note", 500, false));
        i.setUpdatedAt(LocalDateTime.now());
        interests.save(i);
        return Map.of("message", "Saved.");
    }

    // ================================================================ office: placements

    /** Placements of the students the user may see; unconfirmed first. */
    public List<Map<String, Object>> placements(String q, Boolean confirmed) {
        Set<Long> visible = visibleStudentIds();
        Map<Long, User> people = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Placement p : placements.findAllByOrderByIdDesc()) {
            if (!visible.contains(p.getUserId())) continue;
            if (confirmed != null && confirmed != (p.getVerifiedAt() != null)) continue;
            User u = people.computeIfAbsent(p.getUserId(), k -> users.findById(k).orElse(null));
            if (u == null || !com.rahbar.util.Paging.matches(q, u.getName(), u.getUserId(), p.getOrganisation(), p.getRoleTitle(), u.getChapterName())) continue;
            Map<String, Object> r = Rows.of(p);
            r.put("name", u.getName());
            r.put("code", u.getUserId());
            r.put("chapter_name", u.getChapterName());
            r.put("study_status", StudyStatus.label(u.getStudyStatus()));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> r.get("verified_at") != null));
        return rows;
    }

    /** The office records (or corrects) a placement: confirmed straight away. Body: { userId, kind, organisation, ... }. */
    @Transactional
    public Map<String, Object> savePlacement(Long id, Map<String, Object> b) {
        Placement p = id == null ? new Placement() : placements.findById(id).orElseThrow(() -> notFound("Placement"));
        Long userId = id == null ? ServiceSupport.toLong(b.get("userId")) : p.getUserId();
        User u = requireVisibleStudent(userId);
        p.setUserId(u.getId());
        fill(p, b);
        p.setVerifiedAt(LocalDateTime.now());
        p.setVerifiedBy(Access.current().getUser().getId());
        placements.save(p);
        syncAlumni(u, p);
        return Map.of("id", p.getId(), "message", "Placement saved.");
    }

    /** Confirms a placement a student added. */
    @Transactional
    public Map<String, Object> confirmPlacement(Long id) {
        Placement p = placements.findById(id).orElseThrow(() -> notFound("Placement"));
        User u = requireVisibleStudent(p.getUserId());
        p.setVerifiedAt(LocalDateTime.now());
        p.setVerifiedBy(Access.current().getUser().getId());
        placements.save(p);
        syncAlumni(u, p);
        notifications.notify(u.getId(), "Placement confirmed", "The office confirmed your placement at " + p.getOrganisation() + ". Congratulations!",
                "careers", "/student/careers", false);
        return Map.of("message", "Placement confirmed.");
    }

    @Transactional
    public void deletePlacement(Long id) {
        Placement p = placements.findById(id).orElseThrow(() -> notFound("Placement"));
        requireVisibleStudent(p.getUserId());
        placements.delete(p);
    }

    private void fill(Placement p, Map<String, Object> b) {
        p.setKind(code(b.get("kind"), PLACEMENT_KINDS, "JOB", "Kind"));
        p.setOrganisation(text(b.get("organisation"), p.getKind().equals("HIGHER_STUDIES") ? "College / university" : "Organisation", 200, true));
        p.setEmployerId(ServiceSupport.toLong(b.get("employerId")));
        if (p.getEmployerId() != null && employers.findById(p.getEmployerId()).isEmpty()) throw bad("Choose the employer from the list.");
        p.setRoleTitle(text(b.get("roleTitle"), p.getKind().equals("HIGHER_STUDIES") ? "Course" : "Role", 150, false));
        p.setLocation(text(b.get("location"), "Location", 150, false));
        p.setStartDate(date(b.get("startDate"), "Start date"));
        p.setSalaryBand(text(b.get("salaryBand"), "Salary band", 30, false));
        p.setOpportunityId(ServiceSupport.toLong(b.get("opportunityId")));
        p.setNote(text(b.get("note"), "Note", 500, false));
    }

    /**
     * A confirmed job, higher studies or own work of a graduate becomes their alumni "what they do now"
     * (an internship doesn't).
     */
    void syncAlumni(User u, Placement p) {
        if (!StudyStatus.GRADUATED.equals(StudyStatus.of(u.getStudyStatus()))) return;
        String status = switch (p.getKind()) {
            case "JOB" -> "EMPLOYED";
            case "HIGHER_STUDIES" -> "HIGHER_STUDIES";
            case "SELF_EMPLOYED" -> "SELF_EMPLOYED";
            default -> null;
        };
        if (status == null) return;
        AlumniProfile a = alumni.findById(u.getId()).orElseGet(() -> {
            AlumniProfile n = new AlumniProfile();
            n.setUserId(u.getId());
            return n;
        });
        a.setCurrentStatus(status);
        a.setOrganisation(p.getOrganisation());
        a.setRoleTitle(p.getRoleTitle());
        if (p.getLocation() != null) a.setCity(p.getLocation());
        alumni.save(a);
    }

    // ================================================================ office: employers

    public List<Map<String, Object>> employers() {
        Map<Long, Long> placed = new HashMap<>();
        placements.findAll().forEach(p -> { if (p.getEmployerId() != null) placed.merge(p.getEmployerId(), 1L, Long::sum); });
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employer e : employers.findAllByOrderByNameAsc()) {
            Map<String, Object> r = Rows.of(e);
            r.put("placements", placed.getOrDefault(e.getId(), 0L));
            rows.add(r);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> saveEmployer(Long id, Map<String, Object> b) {
        Employer e = id == null ? new Employer() : employers.findById(id).orElseThrow(() -> notFound("Employer"));
        e.setName(text(b.get("name"), "Name", 200, true));
        e.setSector(text(b.get("sector"), "Sector", 100, false));
        e.setCity(text(b.get("city"), "City", 100, false));
        e.setContactPerson(text(b.get("contactPerson"), "Contact person", 150, false));
        e.setContactPhone(text(b.get("contactPhone"), "Phone", 30, false));
        e.setContactEmail(text(b.get("contactEmail"), "Email", 150, false));
        e.setWebsite(link(b.get("website"), "Website"));
        e.setNotes(text(b.get("notes"), "Notes", 1000, false));
        e.setActive(!b.containsKey("active") || flag(b.get("active")));
        employers.save(e);
        return Map.of("id", e.getId(), "message", "Employer saved.");
    }

    @Transactional
    public void deleteEmployer(Long id) {
        Employer e = employers.findById(id).orElseThrow(() -> notFound("Employer"));
        if (placements.findAll().stream().anyMatch(p -> id.equals(p.getEmployerId()))) throw bad("Placements name this employer. Make it inactive instead.");
        employers.delete(e);
    }

    // ================================================================ office: guidance

    public Map<String, Object> guidanceRequests(String status) {
        Set<Long> visible = visibleStudentIds();
        Map<Long, String> names = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GuidanceRequest g : guidance.findAllByOrderByIdDesc()) {
            if (!visible.contains(g.getUserId()) || (status != null && !status.isBlank() && !status.equalsIgnoreCase(g.getStatus()))) continue;
            Map<String, Object> r = Rows.of(g);
            User u = users.findById(g.getUserId()).orElse(null);
            r.put("name", u == null ? "?" : u.getName());
            r.put("code", u == null ? null : u.getUserId());
            r.put("phone", u == null ? null : u.getPhone());
            r.put("chapter_name", u == null ? null : u.getChapterName());
            r.put("assigned_name", g.getAssignedTo() == null ? null
                    : names.computeIfAbsent(g.getAssignedTo(), k -> users.findById(k).map(User::getName).orElse("?")));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("staff", staff());
        return out;
    }

    /** { status, assignedTo, sessionAt, outcome }; the student is told when a session is set or the request is done. */
    @Transactional
    public Map<String, Object> updateGuidance(Long id, Map<String, Object> b) {
        GuidanceRequest g = guidance.findById(id).orElseThrow(() -> notFound("Request"));
        requireVisibleStudent(g.getUserId());
        String before = g.getStatus();
        LocalDateTime sessionBefore = g.getSessionAt();
        g.setStatus(code(b.get("status"), GUIDANCE_STATUSES, g.getStatus(), "Status"));
        g.setAssignedTo(ServiceSupport.toLong(b.get("assignedTo")));
        g.setSessionAt(dateTime(b.get("sessionAt"), "Session"));
        g.setOutcome(text(b.get("outcome"), "Outcome", 2000, false));
        guidance.save(g);
        if (g.getSessionAt() != null && !g.getSessionAt().equals(sessionBefore)) {
            notifications.notify(g.getUserId(), "Career guidance session", "Your career guidance session is on "
                    + g.getSessionAt().toLocalDate() + " at " + g.getSessionAt().toLocalTime() + ".", "careers", "/student/careers", true);
        } else if ("DONE".equals(g.getStatus()) && !"DONE".equals(before)) {
            notifications.notify(g.getUserId(), "Career guidance", "Your career guidance request is done. See the notes on your Careers page.",
                    "careers", "/student/careers", false);
        }
        return Map.of("message", "Saved.");
    }

    /** Staff who can be given a guidance request: active users with an admin role. */
    List<Map<String, Object>> staff() {
        List<Map<String, Object>> out = new ArrayList<>();
        users.findAll().stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getRoleId() != null && !List.of(3, 4, 5, 6).contains(u.getRoleId()))
                .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .forEach(u -> out.add(Map.of("id", u.getId(), "name", u.getName())));
        return out;
    }

    // ================================================================ student portal

    /** Open openings (with whether I'm interested), my placements and my guidance requests. */
    public Map<String, Object> mine(Long me) {
        Map<Long, CareerInterest> my = new HashMap<>();
        interests.findByUserId(me).forEach(i -> my.put(i.getOpportunityId(), i));
        List<Map<String, Object>> open = new ArrayList<>();
        for (CareerOpportunity o : opportunities.findAllByOrderByIdDesc()) {
            if (!isOpen(o) && !my.containsKey(o.getId())) continue;
            Map<String, Object> r = Rows.of(o);
            r.put("open_now", isOpen(o));
            CareerInterest i = my.get(o.getId());
            r.put("my_status", i == null ? null : i.getStatus());
            open.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("opportunities", open);
        out.put("placements", Rows.list(placements.findByUserIdOrderByStartDateDesc(me)));
        out.put("guidance", Rows.list(guidance.findByUserIdOrderByIdDesc(me)));
        return out;
    }

    /** "I'm interested" on / off. */
    @Transactional
    public Map<String, Object> setInterest(Long me, Long opportunityId, boolean interested) {
        CareerOpportunity o = requireOpportunity(opportunityId);
        Optional<CareerInterest> existing = interests.findByOpportunityIdAndUserId(opportunityId, me);
        if (!interested) {
            existing.ifPresent(interests::delete);
            return Map.of("message", "Removed.");
        }
        if (!isOpen(o)) throw bad("This opening is closed.");
        if (existing.isEmpty()) {
            CareerInterest i = new CareerInterest();
            i.setOpportunityId(opportunityId);
            i.setUserId(me);
            i.setCreatedAt(LocalDateTime.now());
            interests.save(i);
        }
        return Map.of("message", "Marked as interested. The office will be in touch" + (o.getApplyLink() == null ? "." : "; you can also apply with the link."));
    }

    /** A student adds their own placement; the office confirms it. */
    @Transactional
    public Map<String, Object> addMyPlacement(Long me, Map<String, Object> b) {
        Placement p = new Placement();
        p.setUserId(me);
        fill(p, b);
        p.setAddedByStudent(true);
        placements.save(p);
        User u = users.findById(me).orElseThrow();
        notifications.notifyRoles(OFFICE_ROLES, "Placement to confirm", u.getName() + " added a placement at " + p.getOrganisation() + ".",
                "careers", "/admin/careers");
        return Map.of("message", "Thank you! The office will confirm it.");
    }

    @Transactional
    public Map<String, Object> askGuidance(Long me, Map<String, Object> b) {
        GuidanceRequest g = new GuidanceRequest();
        g.setUserId(me);
        g.setTopic(code(b.get("topic"), TOPICS, "OTHER", "Topic"));
        g.setMessage(text(b.get("message"), "Your question", 2000, true));
        guidance.save(g);
        User u = users.findById(me).orElseThrow();
        notifications.notifyRoles(OFFICE_ROLES, "Career guidance request", u.getName() + " asked for career guidance.", "careers", "/admin/careers");
        return Map.of("message", "Request sent. The office will arrange a session with you.");
    }

    // ================================================================ helpers

    static boolean isOpen(CareerOpportunity o) {
        return "OPEN".equals(o.getStatus()) && (o.getLastDate() == null || !o.getLastDate().isBefore(LocalDate.now()));
    }

    private CareerOpportunity requireOpportunity(Long id) {
        return opportunities.findById(id).orElseThrow(() -> notFound("Opening"));
    }

    /** Students (and graduates) the signed-in staff member may see. */
    private Set<Long> visibleStudentIds() {
        StudentScope.Scope scope = studentScope.current();
        Set<Long> ids = new HashSet<>();
        users.findByRoleId(ServiceSupport.STUDENT_ROLE).stream().filter(scope::includes).forEach(u -> ids.add(u.getId()));
        return ids;
    }

    private User requireVisibleStudent(Long userId) {
        if (userId == null) throw bad("Choose the student.");
        if (!Access.can(Section.CAREERS, Section.Level.VIEW)) throw Access.forbidden("No access to careers.");
        return studentScope.requireStudent(userId);
    }

    private static String link(Object v, String label) {
        String s = text(v, label, 500, false);
        if (s != null && !s.matches("(?i)https?://\\S+")) throw bad(label + " must start with http:// or https://");
        return s;
    }
}
