package com.rahbar.service;

import com.rahbar.entity.AlumniProfile;
import com.rahbar.entity.Mentorship;
import com.rahbar.entity.User;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.AlumniProfileRepository;
import com.rahbar.repository.MentorshipRepository;
import com.rahbar.repository.StudentInstitutionCourseRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.repository.VolunteerApplicationRepository;
import com.rahbar.security.StudentScope;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Students > Mentoring: pairs scholarship students with volunteers who offered to mentor or tutor (website sign-ups
 * not declined) and with graduates who agreed to be contacted. Suggestions rank mentors by the fields they share with
 * the student's course (engineering, medicine, computing, ...), then by the same chapter, then by fewer current mentees.
 * Chapter / RCC-scoped staff only see and pair their own students and graduates.
 */
@Service
public class MentoringService {

    public static final Map<String, String> HELP = new LinkedHashMap<>();
    static {
        HELP.put("MENTOR", "Mentor");
        HELP.put("TUTOR", "Tutor");
    }
    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    /** Words (and abbreviations) that name the same field. */
    private static final Map<String, String> FIELD = new HashMap<>();
    static {
        field("engineering", "engineering", "engineer", "engineers", "btech", "mtech", "iit", "nit", "polytechnic",
                "mechanical", "civil", "electrical", "electronics", "ece", "eee", "jee");
        field("computing", "computer", "computers", "computing", "cs", "cse", "information", "software", "programming", "coding",
                "developer", "bca", "mca", "data", "ai", "web", "ml");
        field("medicine", "mbbs", "medical", "medicine", "doctor", "doctors", "bds", "dental", "neet", "aiims", "surgery");
        field("health", "nursing", "nurse", "pharmacy", "pharma", "bpharm", "dpharm", "physiotherapy", "bpt", "paramedical", "gnm", "anm");
        field("business", "commerce", "bcom", "mcom", "accounting", "accounts", "accountancy", "ca", "finance", "banking",
                "bank", "mba", "bba", "management", "business", "marketing", "economics");
        field("law", "law", "llb", "llm", "legal", "lawyer", "advocate", "clat");
        field("civil-services", "upsc", "ias", "ips", "bpsc", "ssc", "civil-services");
        field("teaching", "bed", "education", "teacher", "teachers", "teaching", "ded");
        field("mathematics", "maths", "math", "mathematics");
        field("physics", "physics");
        field("chemistry", "chemistry");
        field("biology", "biology", "zoology", "botany", "biotech", "biotechnology");
        field("english", "english");
        field("urdu", "urdu", "arabic");
        field("humanities", "history", "geography", "political", "sociology", "psychology", "philosophy", "arts", "humanities");
        field("agriculture", "agriculture", "agricultural", "veterinary", "fisheries");
        field("design", "design", "architecture", "barch", "nift", "fashion");
        field("media", "journalism", "media", "communication");
    }

    private static void field(String name, String... words) {
        for (String w : words) FIELD.put(w, name);
    }

    private final MentorshipRepository pairs;
    private final VolunteerApplicationRepository volunteers;
    private final AlumniProfileRepository profiles;
    private final UserRepository users;
    private final StudentInstitutionCourseRepository courses;
    private final StudentScope studentScope;

    public MentoringService(MentorshipRepository pairs, VolunteerApplicationRepository volunteers,
                            AlumniProfileRepository profiles, UserRepository users,
                            StudentInstitutionCourseRepository courses, StudentScope studentScope) {
        this.pairs = pairs;
        this.volunteers = volunteers;
        this.profiles = profiles;
        this.users = users;
        this.courses = courses;
        this.studentScope = studentScope;
    }

    // ---------------------------------------------------------------- fields

    /**
     * The known fields a piece of text talks about: "B.Tech (CSE), NIT Patna" -> engineering, computing. Other words
     * are ignored, so two people writing "careers" or "companies" don't look like a match.
     */
    static Set<String> fields(String... texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : texts) {
            if (t == null) continue;
            String s = t.toLowerCase(Locale.ROOT).replace(".", "").replace("civil services", "civil-services");
            for (String w : s.split("[^a-z0-9-]+")) {
                String f = FIELD.get(w);
                if (f != null) out.add(f);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- lists

    /** Every pair (own students only for scoped staff), with counts. */
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        StudentScope.Scope scope = studentScope.current();
        Map<Long, User> byId = usersById();
        Map<Long, VolunteerApplication> vols = volunteersById();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Mentorship m : pairs.findAllByOrderByStatusAscStartedOnDescIdDesc()) {
            User s = byId.get(m.getStudentUserId());
            if (s == null || !scope.includes(s)) continue;
            rows.add(pairRow(m, s, byId, vols));
        }
        Set<Long> paired = new HashSet<>();
        rows.stream().filter(r -> ACTIVE.equals(r.get("status"))).forEach(r -> paired.add((Long) r.get("student_id")));
        long waiting = students(scope, byId).stream().filter(u -> !paired.contains(u.getId())).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rows", rows);
        body.put("active", rows.stream().filter(r -> ACTIVE.equals(r.get("status"))).count());
        body.put("waiting", waiting);
        body.put("mentors", mentors(scope, byId, vols).size());
        body.put("help", HELP);
        return body;
    }

    /** Scholarship students still in the programme, with their course and how many mentors they have now. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> studentList() {
        StudentScope.Scope scope = studentScope.current();
        Map<Long, User> byId = usersById();
        Map<Long, Long> load = new HashMap<>();
        pairs.findByStatus(ACTIVE).forEach(m -> load.merge(m.getStudentUserId(), 1L, Long::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : students(scope, byId)) {
            Map<String, Object> r = studentRow(u);
            r.put("mentors_now", load.getOrDefault(u.getId(), 0L));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("mentors_now"))
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Everyone who can mentor now, with the fields they cover and their current mentees. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mentorList() {
        List<Map<String, Object>> rows = mentors(studentScope.current(), usersById(), volunteersById());
        rows.forEach(r -> r.remove("fieldSet"));
        return rows;
    }

    /** The student, and every available mentor ranked for them (best first). */
    @Transactional(readOnly = true)
    public Map<String, Object> suggestions(Long studentId) {
        User s = requireStudent(studentId);
        Map<Long, User> byId = usersById();
        Map<String, Object> student = studentRow(s);
        Set<String> want = fields((String) student.get("course"), (String) student.get("institution"));
        student.put("fields", want);
        Set<String> already = new HashSet<>();
        pairs.findByStatus(ACTIVE).stream().filter(m -> studentId.equals(m.getStudentUserId())).forEach(m -> already.add(mentorKey(m)));

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> m : mentors(studentScope.current(), byId, volunteersById())) {
            if (already.contains(m.get("key")) || studentId.equals(m.get("alumni_user_id"))) continue;
            @SuppressWarnings("unchecked") Set<String> has = (Set<String>) m.remove("fieldSet");
            List<String> shared = has.stream().filter(want::contains).toList();
            boolean sameChapter = s.getChapterId() != null && s.getChapterId().equals(m.get("chapter_id"));
            m.put("shared_fields", shared);
            m.put("same_chapter", sameChapter);
            m.put("score", shared.size() * 2 + (sameChapter ? 1 : 0));
            matches.add(m);
        }
        matches.sort(Comparator.comparing((Map<String, Object> m) -> -(Integer) m.get("score"))
                .thenComparing(m -> (Long) m.get("mentees_now"))
                .thenComparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("student", student);
        body.put("matches", matches);
        return body;
    }

    // ---------------------------------------------------------------- changes

    /** Body: { studentId, volunteerId | alumniUserId, help MENTOR | TUTOR, focus, startedOn, notes }. */
    @Transactional
    public Mentorship pair(Map<String, Object> body) {
        Long studentId = id(body.get("studentId"), "Please choose a student.");
        User s = requireStudent(studentId);
        if (!StudyStatus.inProgramme(s.getStudyStatus())) throw bad("This student is no longer in the programme.");
        Long volunteerId = optionalId(body.get("volunteerId"));
        Long alumniId = optionalId(body.get("alumniUserId"));
        if ((volunteerId == null) == (alumniId == null)) throw bad("Please choose one volunteer or one graduate.");
        String help = ServiceSupport.str(body.get("help"));
        if (help == null || !HELP.containsKey(help)) throw bad("Please choose mentor or tutor.");

        if (volunteerId != null) {
            VolunteerApplication v = volunteers.findById(volunteerId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Volunteer not found."));
            if ("DECLINED".equals(v.getStatus())) throw bad("This volunteer's sign-up was declined.");
            if ("MENTOR".equals(help) && !v.isWantsMentor()) throw bad(v.getFullName() + " offered to tutor, not to mentor.");
            if ("TUTOR".equals(help) && !v.isWantsTutor()) throw bad(v.getFullName() + " offered to mentor, not to tutor.");
        } else {
            if (alumniId.equals(studentId)) throw bad("A student can't mentor themselves.");
            User a = studentScope.requireStudent(alumniId);
            AlumniProfile p = profiles.findById(alumniId).orElse(null);
            if (p == null || !Boolean.TRUE.equals(p.getConsentToContact()) || a.getMergedIntoId() != null) {
                throw bad(a.getName() + " hasn't agreed to be contacted. Ask them to tick it on their alumni profile.");
            }
        }
        Mentorship m = new Mentorship();
        m.setStudentUserId(studentId);
        m.setVolunteerId(volunteerId);
        m.setAlumniUserId(alumniId);
        String key = mentorKey(m);
        if (pairs.findByStatus(ACTIVE).stream().anyMatch(x -> studentId.equals(x.getStudentUserId()) && key.equals(mentorKey(x)))) {
            throw bad("They are already paired.");
        }
        m.setHelp(help);
        m.setFocus(text(body.get("focus"), 200));
        LocalDate start = date(body.get("startedOn"));
        m.setStartedOn(start == null ? LocalDate.now() : start);
        m.setNotes(text(body.get("notes"), 1000));
        return pairs.save(m);
    }

    /** Body: any of { status ACTIVE | ENDED, endedOn, focus, notes }. Ending a pair dates it today unless endedOn is given. */
    @Transactional
    public Mentorship update(Long id, Map<String, Object> body) {
        Mentorship m = pairs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pair not found."));
        requireStudent(m.getStudentUserId());
        if (body.containsKey("focus")) m.setFocus(text(body.get("focus"), 200));
        if (body.containsKey("notes")) m.setNotes(text(body.get("notes"), 1000));
        if (body.containsKey("status")) {
            String status = ServiceSupport.str(body.get("status"));
            if (!ACTIVE.equals(status) && !ENDED.equals(status)) throw bad("Unknown status.");
            if (ENDED.equals(status)) {
                LocalDate end = date(body.get("endedOn"));
                m.setEndedOn(end == null ? LocalDate.now() : end);
                if (m.getEndedOn().isBefore(m.getStartedOn())) throw bad("The end date is before the start date.");
            } else {
                m.setEndedOn(null);
            }
            m.setStatus(status);
        }
        return pairs.save(m);
    }

    // ---------------------------------------------------------------- helpers

    private List<User> students(StudentScope.Scope scope, Map<Long, User> byId) {
        return byId.values().stream()
                .filter(u -> Integer.valueOf(ServiceSupport.STUDENT_ROLE).equals(u.getRoleId()))
                .filter(u -> u.getMergedIntoId() == null && StudyStatus.inProgramme(u.getStudyStatus()))
                .filter(scope::includes)
                .toList();
    }

    /** Volunteers who offered to mentor or tutor (not declined), and graduates who agreed to be contacted. */
    private List<Map<String, Object>> mentors(StudentScope.Scope scope, Map<Long, User> byId, Map<Long, VolunteerApplication> vols) {
        Map<String, Long> load = new HashMap<>();
        pairs.findByStatus(ACTIVE).forEach(m -> load.merge(mentorKey(m), 1L, Long::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (VolunteerApplication v : vols.values()) {
            if (!(v.isWantsMentor() || v.isWantsTutor()) || "DECLINED".equals(v.getStatus())) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("key", "V" + v.getVolunteerId());
            r.put("kind", "VOLUNTEER");
            r.put("volunteer_id", v.getVolunteerId());
            r.put("name", v.getFullName());
            r.put("email", v.getEmail());
            r.put("phone", v.getPhone());
            r.put("city", v.getCity());
            r.put("chapter_id", v.getChapterId());
            r.put("about", join(v.getProfession(), v.getMentorFields(), v.getTutorSubjects()));
            r.put("offers", offers(v.isWantsMentor(), v.isWantsTutor()));
            r.put("hours_per_month", v.getHoursPerMonth());
            r.put("status", VolunteerApplication.STATUSES.getOrDefault(v.getStatus(), v.getStatus()));
            Set<String> f = fields(v.getProfession(), v.getMentorFields(), v.getTutorSubjects(), v.getTutorGrades());
            r.put("fieldSet", f);
            r.put("fields", f);
            r.put("mentees_now", load.getOrDefault("V" + v.getVolunteerId(), 0L));
            rows.add(r);
        }
        for (AlumniProfile p : profiles.findAll()) {
            User a = byId.get(p.getUserId());
            if (a == null || !Boolean.TRUE.equals(p.getConsentToContact()) || a.getMergedIntoId() != null || !scope.includes(a)) continue;
            Map<String, Object> course = Rows.first(courses.findCourseDetails(a.getId()));
            String courseName = course == null ? null : (String) course.get("course_name");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("key", "A" + a.getId());
            r.put("kind", "ALUMNI");
            r.put("alumni_user_id", a.getId());
            r.put("user_id", a.getUserId());
            r.put("name", a.getName());
            r.put("email", a.getEmail());
            r.put("phone", a.getPhone());
            r.put("city", p.getCity());
            r.put("chapter_id", a.getChapterId());
            r.put("about", join(courseName, p.getRoleTitle(), p.getOrganisation()));
            r.put("offers", offers(true, true));
            r.put("hours_per_month", null);
            r.put("status", p.getCurrentStatus() == null ? null : AlumniProfile.STATUSES.get(p.getCurrentStatus()));
            Set<String> f = fields(courseName, p.getRoleTitle());
            r.put("fieldSet", f);
            r.put("fields", f);
            r.put("mentees_now", load.getOrDefault("A" + a.getId(), 0L));
            rows.add(r);
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private Map<String, Object> pairRow(Mentorship m, User s, Map<Long, User> byId, Map<Long, VolunteerApplication> vols) {
        Map<String, Object> r = new LinkedHashMap<>(Rows.of(m));
        r.put("student_id", s.getId());
        r.put("student_ref_id", s.getUserId());
        r.put("student_name", s.getName());
        r.put("chapter", s.getChapterName());
        r.put("help_label", HELP.getOrDefault(m.getHelp(), m.getHelp()));
        if (m.getVolunteerId() != null) {
            VolunteerApplication v = vols.get(m.getVolunteerId());
            r.put("mentor_kind", "VOLUNTEER");
            r.put("mentor_name", v == null ? "(sign-up deleted)" : v.getFullName());
            r.put("mentor_email", v == null ? null : v.getEmail());
            r.put("mentor_phone", v == null ? null : v.getPhone());
        } else {
            User a = m.getAlumniUserId() == null ? null : byId.get(m.getAlumniUserId());
            r.put("mentor_kind", "ALUMNI");
            r.put("mentor_name", a == null ? "(graduate removed)" : a.getName());
            r.put("mentor_email", a == null ? null : a.getEmail());
            r.put("mentor_phone", a == null ? null : a.getPhone());
        }
        return r;
    }

    private Map<String, Object> studentRow(User u) {
        Map<String, Object> course = Rows.first(courses.findCourseDetails(u.getId()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId());
        r.put("user_id", u.getUserId());
        r.put("name", u.getName());
        r.put("chapter", u.getChapterName());
        r.put("course", course == null ? null : course.get("course_name"));
        r.put("institution", course == null ? null : course.get("institution_name"));
        r.put("study_status", StudyStatus.label(u.getStudyStatus()));
        return r;
    }

    private User requireStudent(Long id) {
        User u = studentScope.requireStudent(id);
        if (!Integer.valueOf(ServiceSupport.STUDENT_ROLE).equals(u.getRoleId())) throw new ApiException(HttpStatus.NOT_FOUND, "Student not found");
        return u;
    }

    private Map<Long, User> usersById() {
        Map<Long, User> m = new HashMap<>();
        users.findByRoleId(ServiceSupport.STUDENT_ROLE).forEach(u -> m.put(u.getId(), u));
        return m;
    }

    private Map<Long, VolunteerApplication> volunteersById() {
        Map<Long, VolunteerApplication> m = new LinkedHashMap<>();
        volunteers.findAllByOrderByCreatedAtDesc().forEach(v -> m.put(v.getVolunteerId(), v));
        return m;
    }

    private static String mentorKey(Mentorship m) {
        return m.getVolunteerId() != null ? "V" + m.getVolunteerId() : "A" + m.getAlumniUserId();
    }

    private static List<String> offers(boolean mentor, boolean tutor) {
        List<String> out = new ArrayList<>();
        if (mentor) out.add("MENTOR");
        if (tutor) out.add("TUTOR");
        return out;
    }

    private static String join(String... parts) {
        StringJoiner j = new StringJoiner(" · ");
        for (String p : parts) if (p != null && !p.isBlank()) j.add(p.strip());
        return j.length() == 0 ? null : j.toString();
    }

    private static String text(Object v, int max) {
        String s = ServiceSupport.str(v);
        if (s == null || s.isBlank()) return null;
        s = s.strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Long optionalId(Object v) {
        if (ServiceSupport.isBlank(v)) return null;
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw bad("Unknown id.");
        }
    }

    private static Long id(Object v, String missing) {
        Long id = optionalId(v);
        if (id == null) throw bad(missing);
        return id;
    }

    private static LocalDate date(Object v) {
        if (ServiceSupport.isBlank(v)) return null;
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (DateTimeParseException e) {
            throw bad("Please give the date as YYYY-MM-DD.");
        }
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
