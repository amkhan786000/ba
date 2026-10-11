package com.rahbar.reb;

import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.rahbar.reb.RebSupport.*;

/**
 * REB Setup: sessions (each with its own dates), class levels, sections of a session, subjects (Madrasa / School
 * track), the curriculum of a class for a session, and school settings.
 */
@Service
public class RebSetupService {

    static final Set<String> ASSESSMENTS = Set.of("WRITTEN", "ORAL", "PRACTICAL");

    /** Settings that can be changed, with their labels (shown in this order). */
    static final Map<String, String> SETTINGS = new LinkedHashMap<>();
    static {
        SETTINGS.put("school_name", "School name");
        SETTINGS.put("school_address", "Address");
        SETTINGS.put("school_phone", "Phone");
        SETTINGS.put("principal_name", "Principal / Nazima");
        SETTINGS.put("admission_prefix", "Admission number prefix");
        SETTINGS.put("board_name", "Board");
    }

    private final RebSessionRepository sessions;
    private final RebClassRepository classes;
    private final RebSectionRepository sections;
    private final RebSubjectRepository subjects;
    private final RebClassSubjectRepository classSubjects;
    private final RebSettingRepository settings;
    private final RebStaffRepository staff;
    private final RebEnrollmentRepository enrollments;
    private final RebTeacherAssignmentRepository assignments;

    public RebSetupService(RebSessionRepository sessions, RebClassRepository classes, RebSectionRepository sections,
                           RebSubjectRepository subjects, RebClassSubjectRepository classSubjects, RebSettingRepository settings,
                           RebStaffRepository staff, RebEnrollmentRepository enrollments, RebTeacherAssignmentRepository assignments) {
        this.sessions = sessions;
        this.classes = classes;
        this.sections = sections;
        this.subjects = subjects;
        this.classSubjects = classSubjects;
        this.settings = settings;
        this.staff = staff;
        this.enrollments = enrollments;
        this.assignments = assignments;
    }

    // ---------------------------------------------------------------- sessions

    public List<Map<String, Object>> sessions() {
        return Rows.list(sessions.findAllByOrderByStartDateDesc());
    }

    /** The current session; 400 when none was set up yet. */
    public RebSession currentSession() {
        return sessions.findFirstByCurrentTrue()
                .orElseThrow(() -> bad("There is no current session yet. Add one in REB Setup > Sessions."));
    }

    /** The current session's id, or null when there is none yet. */
    public Long sessionsCurrentId() {
        return sessions.findFirstByCurrentTrue().map(RebSession::getId).orElse(null);
    }

    /** The given session, or the current one when no id is given. */
    public RebSession session(Long id) {
        if (id == null) return currentSession();
        return sessions.findById(id).orElseThrow(() -> notFound("Session"));
    }

    @Transactional
    public RebSession saveSession(Long id, Map<String, Object> body) {
        RebSession s = id == null ? new RebSession() : sessions.findById(id).orElseThrow(() -> notFound("Session"));
        s.setName(text(body.get("name"), "Name", 30, true));
        if (id == null ? sessions.existsByName(s.getName()) : sessions.existsByNameAndIdNot(s.getName(), id)) {
            throw bad("There is already a session called " + s.getName() + ".");
        }
        s.setStartDate(date(body.get("startDate"), "Start date"));
        s.setEndDate(date(body.get("endDate"), "End date"));
        if (s.getStartDate() == null || s.getEndDate() == null) throw bad("Start and end dates are required.");
        if (!s.getEndDate().isAfter(s.getStartDate())) throw bad("The end date must be after the start date.");
        if (body.containsKey("status")) s.setStatus(code(body.get("status"), Set.of("OPEN", "CLOSED"), "OPEN", "status"));
        boolean first = sessions.count() == 0;
        if (first) s.setCurrent(true); // the first session is the current one
        return sessions.save(s);
    }

    @Transactional
    public void makeCurrent(Long id) {
        RebSession target = sessions.findById(id).orElseThrow(() -> notFound("Session"));
        for (RebSession s : sessions.findAll()) {
            boolean current = s.getId().equals(target.getId());
            if (s.isCurrent() != current) {
                s.setCurrent(current);
                sessions.save(s);
            }
        }
    }

    // ---------------------------------------------------------------- classes

    public List<Map<String, Object>> classes() {
        return Rows.list(classes.findAllByOrderByLevelOrderAscNameAsc());
    }

    @Transactional
    public RebClass saveClass(Long id, Map<String, Object> body) {
        RebClass c = id == null ? new RebClass() : classes.findById(id).orElseThrow(() -> notFound("Class"));
        c.setName(text(body.get("name"), "Name", 80, true));
        if (id == null ? classes.existsByName(c.getName()) : classes.existsByNameAndIdNot(c.getName(), id)) {
            throw bad("There is already a class called " + c.getName() + ".");
        }
        c.setTrack(code(body.get("track"), TRACKS, "BOTH", "track"));
        Integer order = number(body.get("levelOrder"), "Order", 0, 1000);
        c.setLevelOrder(order == null ? 0 : order);
        Long next = id(body.get("nextClassId"));
        if (next != null && (next.equals(c.getId()) || !classes.existsById(next))) throw bad("Choose another class to promote to.");
        c.setNextClassId(next);
        c.setBoardExam(flag(body.get("boardExam"), false));
        c.setActive(flag(body.get("active"), true));
        return classes.save(c);
    }

    @Transactional
    public void deleteClass(Long id) {
        RebClass c = classes.findById(id).orElseThrow(() -> notFound("Class"));
        if (sections.existsByClassId(id) || classSubjects.existsByClassId(id)) {
            throw bad(c.getName() + " is used by sections or a curriculum; hide it instead (untick Active).");
        }
        classes.delete(c);
    }

    // ---------------------------------------------------------------- sections

    /** Sections of a session (default: the current one) with class, class teacher and number of girls. */
    public List<Map<String, Object>> sections(Long sessionId) {
        RebSession session = session(sessionId);
        Map<Long, RebClass> classById = new HashMap<>();
        classes.findAll().forEach(c -> classById.put(c.getId(), c));
        Map<Long, String> staffNames = new HashMap<>();
        staff.findAll().forEach(s -> staffNames.put(s.getId(), s.getName()));
        Map<Long, Long> girls = new HashMap<>();
        for (RebEnrollment e : enrollments.findBySessionId(session.getId())) {
            if ("ACTIVE".equals(e.getStatus())) girls.merge(e.getSectionId(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<RebSection> list = new ArrayList<>(sections.findBySessionId(session.getId()));
        list.sort(Comparator.comparing((RebSection s) -> classById.get(s.getClassId()) == null ? 0 : classById.get(s.getClassId()).getLevelOrder())
                .thenComparing(s -> classById.get(s.getClassId()) == null ? "" : classById.get(s.getClassId()).getName())
                .thenComparing(RebSection::getName));
        for (RebSection s : list) {
            Map<String, Object> r = Rows.of(s);
            RebClass c = classById.get(s.getClassId());
            r.put("class_name", c == null ? null : c.getName());
            r.put("track", c == null ? null : c.getTrack());
            r.put("label", label(c, s));
            r.put("class_teacher_name", staffNames.get(s.getClassTeacherStaffId()));
            r.put("girls", girls.getOrDefault(s.getId(), 0L));
            rows.add(r);
        }
        return rows;
    }

    /** "Class 6 A" (or just "Hifz" when the section is the only one, named A). */
    static String label(RebClass c, RebSection s) {
        String name = c == null ? "?" : c.getName();
        return s == null ? name : name + " " + s.getName();
    }

    @Transactional
    public RebSection saveSection(Long id, Map<String, Object> body) {
        RebSection s = id == null ? new RebSection() : sections.findById(id).orElseThrow(() -> notFound("Section"));
        if (id == null) {
            s.setSessionId(session(id(body.get("sessionId"))).getId());
            Long classId = id(body.get("classId"));
            if (classId == null || !classes.existsById(classId)) throw bad("Choose the class.");
            s.setClassId(classId);
        }
        String name = text(body.get("name"), "Section", 20, false);
        s.setName(name == null ? "A" : name.toUpperCase(Locale.ROOT));
        if (id == null ? sections.existsBySessionIdAndClassIdAndName(s.getSessionId(), s.getClassId(), s.getName())
                : sections.existsBySessionIdAndClassIdAndNameAndIdNot(s.getSessionId(), s.getClassId(), s.getName(), id)) {
            throw bad("This class already has a section " + s.getName() + " in this session.");
        }
        Long teacher = id(body.get("classTeacherStaffId"));
        if (teacher != null && !staff.existsById(teacher)) throw bad("Unknown teacher.");
        s.setClassTeacherStaffId(teacher);
        s.setRoom(text(body.get("room"), "Room", 40, false));
        s.setCapacity(number(body.get("capacity"), "Capacity", 1, 500));
        return sections.save(s);
    }

    @Transactional
    public void deleteSection(Long id) {
        RebSection s = sections.findById(id).orElseThrow(() -> notFound("Section"));
        if (enrollments.existsBySectionId(id)) throw bad("Girls are enrolled in this section; move them first.");
        sections.delete(s);
    }

    // ---------------------------------------------------------------- subjects

    public List<Map<String, Object>> subjects() {
        return Rows.list(subjects.findAllByOrderByTrackAscNameAsc());
    }

    @Transactional
    public RebSubject saveSubject(Long id, Map<String, Object> body) {
        RebSubject s = id == null ? new RebSubject() : subjects.findById(id).orElseThrow(() -> notFound("Subject"));
        s.setName(text(body.get("name"), "Name", 80, true));
        if (id == null ? subjects.existsByName(s.getName()) : subjects.existsByNameAndIdNot(s.getName(), id)) {
            throw bad("There is already a subject called " + s.getName() + ".");
        }
        String code = text(body.get("code"), "Code", 20, false);
        s.setCode(code == null ? null : code.toUpperCase(Locale.ROOT));
        s.setTrack(code(body.get("track"), TRACKS, "SCHOOL", "track"));
        s.setAssessment(code(body.get("assessment"), ASSESSMENTS, "WRITTEN", "assessment"));
        s.setActive(flag(body.get("active"), true));
        try {
            return subjects.saveAndFlush(s);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw bad("Another subject already uses the code " + s.getCode() + ".");
        }
    }

    @Transactional
    public void deleteSubject(Long id) {
        RebSubject s = subjects.findById(id).orElseThrow(() -> notFound("Subject"));
        if (classSubjects.existsBySubjectId(id) || assignments.existsBySubjectId(id)) {
            throw bad(s.getName() + " is in a curriculum; hide it instead (untick Active).");
        }
        subjects.delete(s);
    }

    // ---------------------------------------------------------------- curriculum

    /** The subjects of a class in a session, with marks. */
    public List<Map<String, Object>> curriculum(Long sessionId, Long classId) {
        RebSession session = session(sessionId);
        Map<Long, RebSubject> byId = new HashMap<>();
        subjects.findAll().forEach(s -> byId.put(s.getId(), s));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebClassSubject cs : classSubjects.findBySessionIdAndClassIdOrderByDisplayOrderAscIdAsc(session.getId(), classId)) {
            Map<String, Object> r = Rows.of(cs);
            RebSubject s = byId.get(cs.getSubjectId());
            r.put("subject_name", s == null ? null : s.getName());
            r.put("track", s == null ? null : s.getTrack());
            rows.add(r);
        }
        return rows;
    }

    /** Replaces the curriculum of a class for a session. Body: { sessionId, classId, subjects: [{subjectId, maxMarks, passMarks}] }. */
    @Transactional
    public void saveCurriculum(Map<String, Object> body) {
        RebSession session = session(id(body.get("sessionId")));
        Long classId = id(body.get("classId"));
        if (classId == null || !classes.existsById(classId)) throw bad("Choose the class.");
        if (!(body.get("subjects") instanceof List<?> list)) throw bad("Send the list of subjects.");
        Map<Long, RebClassSubject> existing = new HashMap<>();
        classSubjects.findBySessionIdAndClassIdOrderByDisplayOrderAscIdAsc(session.getId(), classId).forEach(cs -> existing.put(cs.getSubjectId(), cs));
        Set<Long> keep = new HashSet<>();
        int order = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid subject row.");
            Long subjectId = id(m.get("subjectId"));
            if (subjectId == null || !subjects.existsById(subjectId)) throw bad("Unknown subject.");
            if (!keep.add(subjectId)) continue;
            RebClassSubject cs = existing.getOrDefault(subjectId, new RebClassSubject());
            cs.setSessionId(session.getId());
            cs.setClassId(classId);
            cs.setSubjectId(subjectId);
            Integer max = number(m.get("maxMarks"), "Maximum marks", 1, 1000);
            Integer pass = number(m.get("passMarks"), "Pass marks", 0, 1000);
            cs.setMaxMarks(max == null ? 100 : max);
            cs.setPassMarks(pass == null ? Math.round(cs.getMaxMarks() * 0.33f) : pass);
            if (cs.getPassMarks() > cs.getMaxMarks()) throw bad("Pass marks can't be more than the maximum marks.");
            cs.setDisplayOrder(order++);
            classSubjects.save(cs);
        }
        existing.forEach((subjectId, cs) -> { if (!keep.contains(subjectId)) classSubjects.delete(cs); });
    }

    /** Copies every class's curriculum from one session to another (classes that already have one are left alone). */
    @Transactional
    public int copyCurriculum(Long fromSessionId, Long toSessionId) {
        RebSession from = session(fromSessionId), to = session(toSessionId);
        if (from.getId().equals(to.getId())) throw bad("Choose two different sessions.");
        Set<Long> classesDone = new HashSet<>();
        classSubjects.findBySessionId(to.getId()).forEach(cs -> classesDone.add(cs.getClassId()));
        int copied = 0;
        for (RebClassSubject cs : classSubjects.findBySessionId(from.getId())) {
            if (classesDone.contains(cs.getClassId())) continue;
            RebClassSubject n = new RebClassSubject();
            n.setSessionId(to.getId());
            n.setClassId(cs.getClassId());
            n.setSubjectId(cs.getSubjectId());
            n.setMaxMarks(cs.getMaxMarks());
            n.setPassMarks(cs.getPassMarks());
            n.setDisplayOrder(cs.getDisplayOrder());
            classSubjects.save(n);
            copied++;
        }
        return copied;
    }

    // ---------------------------------------------------------------- settings

    public List<Map<String, Object>> settings() {
        Map<String, String> values = settingValues();
        List<Map<String, Object>> rows = new ArrayList<>();
        SETTINGS.forEach((key, label) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("key", key);
            r.put("label", label);
            r.put("value", values.getOrDefault(key, ""));
            rows.add(r);
        });
        return rows;
    }

    public Map<String, String> settingValues() {
        Map<String, String> values = new HashMap<>();
        settings.findAll().forEach(s -> values.put(s.getKey(), s.getValue() == null ? "" : s.getValue()));
        return values;
    }

    public String setting(String key, String fallback) {
        String v = settingValues().get(key);
        return v == null || v.isBlank() ? fallback : v.strip();
    }

    @Transactional
    public void saveSettings(Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!SETTINGS.containsKey(e.getKey())) continue;
            String value = text(e.getValue(), SETTINGS.get(e.getKey()), 1000, false);
            if ("admission_prefix".equals(e.getKey()) && value != null && !value.matches("[A-Za-z0-9-]{1,10}")) {
                throw bad("Admission number prefix: up to 10 letters, digits or dashes.");
            }
            RebSetting s = settings.findById(e.getKey()).orElseGet(() -> {
                RebSetting n = new RebSetting();
                n.setKey(e.getKey());
                return n;
            });
            s.setValue(value);
            settings.save(s);
        }
    }
}
