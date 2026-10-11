package com.rahbar.reb;

import com.rahbar.entity.User;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.rahbar.reb.RebSupport.*;

/** REB teachers and staff, and who teaches which subject in which section. */
@Service
public class RebStaffService {

    static final Set<String> DESIGNATIONS = Set.of("TEACHER", "PRINCIPAL", "WARDEN", "OFFICE", "OTHER");

    private final RebStaffRepository staff;
    private final RebTeacherAssignmentRepository assignments;
    private final RebSectionRepository sections;
    private final RebClassRepository classes;
    private final RebSubjectRepository subjects;
    private final RebClassSubjectRepository classSubjects;
    private final RebSetupService setup;
    private final UserRepository users;

    public RebStaffService(RebStaffRepository staff, RebTeacherAssignmentRepository assignments, RebSectionRepository sections,
                           RebClassRepository classes, RebSubjectRepository subjects, RebClassSubjectRepository classSubjects,
                           RebSetupService setup, UserRepository users) {
        this.staff = staff;
        this.assignments = assignments;
        this.sections = sections;
        this.classes = classes;
        this.subjects = subjects;
        this.classSubjects = classSubjects;
        this.setup = setup;
        this.users = users;
    }

    /** Everyone, with the Rahbar login they use (if any) and how many subjects they teach this session. */
    public List<Map<String, Object>> list() {
        Map<Long, Long> teaching = new HashMap<>();
        Long current = setup.sessionsCurrentId();
        if (current != null) assignments.findBySessionId(current).forEach(a -> teaching.merge(a.getStaffId(), 1L, Long::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebStaff s : staff.findAllByOrderByNameAsc()) {
            Map<String, Object> r = Rows.of(s);
            r.put("login", s.getUserId() == null ? null : users.findById(s.getUserId()).map(User::getEmail).orElse(null));
            r.put("subjects_this_session", teaching.getOrDefault(s.getId(), 0L));
            rows.add(r);
        }
        return rows;
    }

    public Map<String, Object> get(Long id) {
        RebStaff s = staff.findById(id).orElseThrow(() -> notFound("Staff member"));
        Map<String, Object> r = Rows.of(s);
        r.put("login", s.getUserId() == null ? null : users.findById(s.getUserId()).map(User::getEmail).orElse(null));
        return r;
    }

    /**
     * Body: name, designation, phone, email, qualification, joinedOn, leftOn, status, notes, loginEmail (the email of
     * an existing Rahbar user, to link her login; empty to unlink).
     */
    @Transactional
    public RebStaff save(Long id, Map<String, Object> body) {
        RebStaff s = id == null ? new RebStaff() : staff.findById(id).orElseThrow(() -> notFound("Staff member"));
        s.setName(text(body.get("name"), "Name", 120, true));
        s.setDesignation(code(body.get("designation"), DESIGNATIONS, "TEACHER", "designation"));
        s.setPhone(phone(body.get("phone"), "Phone"));
        s.setEmail(text(body.get("email"), "Email", 150, false));
        s.setQualification(text(body.get("qualification"), "Qualification", 150, false));
        s.setJoinedOn(date(body.get("joinedOn"), "Joined on"));
        s.setLeftOn(date(body.get("leftOn"), "Left on"));
        s.setStatus(code(body.get("status"), Set.of("ACTIVE", "LEFT"), "ACTIVE", "status"));
        s.setNotes(text(body.get("notes"), "Notes", 1000, false));
        if (body.containsKey("loginEmail")) {
            String email = text(body.get("loginEmail"), "Login", 150, false);
            if (email == null) {
                s.setUserId(null);
            } else {
                User u = users.findFirstByEmailIgnoreCaseOrderByIdAsc(email)
                        .orElseThrow(() -> bad("No Rahbar user has the email " + email + ". Add the user in Manage Users first."));
                staff.findByUserId(u.getId()).filter(o -> !o.getId().equals(s.getId()))
                        .ifPresent(o -> { throw bad("That login is already linked to " + o.getName() + "."); });
                s.setUserId(u.getId());
            }
        }
        return staff.save(s);
    }

    // ---------------------------------------------------------------- who teaches what

    /**
     * Every subject of every section of a session (from the curriculum of its class), with its teacher (if any):
     * { sections: [{ id, label, subjects: [{ subject_id, subject_name, staff_id }] }], staff: [...] }.
     */
    public Map<String, Object> assignments(Long sessionId) {
        RebSession session = setup.session(sessionId);
        Map<Long, RebClass> classById = new HashMap<>();
        classes.findAll().forEach(c -> classById.put(c.getId(), c));
        Map<Long, RebSubject> subjectById = new HashMap<>();
        subjects.findAll().forEach(s -> subjectById.put(s.getId(), s));
        Map<String, Long> teacher = new HashMap<>();
        assignments.findBySessionId(session.getId()).forEach(a -> teacher.put(a.getSectionId() + ":" + a.getSubjectId(), a.getStaffId()));
        Map<Long, List<RebClassSubject>> curriculum = new HashMap<>();
        classSubjects.findBySessionId(session.getId()).forEach(cs -> curriculum.computeIfAbsent(cs.getClassId(), k -> new ArrayList<>()).add(cs));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> sec : setup.sections(session.getId())) {
            Long sectionId = (Long) sec.get("id"), classId = (Long) sec.get("class_id");
            List<Map<String, Object>> subjectRows = new ArrayList<>();
            List<RebClassSubject> list = new ArrayList<>(curriculum.getOrDefault(classId, List.of()));
            list.sort(Comparator.comparingInt(RebClassSubject::getDisplayOrder));
            for (RebClassSubject cs : list) {
                RebSubject subj = subjectById.get(cs.getSubjectId());
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("subject_id", cs.getSubjectId());
                r.put("subject_name", subj == null ? null : subj.getName());
                r.put("track", subj == null ? null : subj.getTrack());
                r.put("staff_id", teacher.get(sectionId + ":" + cs.getSubjectId()));
                subjectRows.add(r);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sectionId);
            row.put("label", sec.get("label"));
            row.put("subjects", subjectRows);
            out.add(row);
        }
        List<Map<String, Object>> teachers = staff.findAllByOrderByNameAsc().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .map(s -> Map.<String, Object>of("id", s.getId(), "name", s.getName())).toList();
        return Map.of("session", Rows.of(session), "sections", out, "staff", teachers);
    }

    /** Body: { sessionId, assignments: [{ sectionId, subjectId, staffId (empty = nobody) }] }. */
    @Transactional
    public void saveAssignments(Map<String, Object> body) {
        RebSession session = setup.session(id(body.get("sessionId")));
        if (!(body.get("assignments") instanceof List<?> list)) throw bad("Send the list of assignments.");
        Map<String, RebTeacherAssignment> existing = new HashMap<>();
        assignments.findBySessionId(session.getId()).forEach(a -> existing.put(a.getSectionId() + ":" + a.getSubjectId(), a));
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid assignment.");
            Long sectionId = id(m.get("sectionId")), subjectId = id(m.get("subjectId")), staffId = id(m.get("staffId"));
            RebSection sec = sectionId == null ? null : sections.findById(sectionId).orElse(null);
            if (sec == null || !sec.getSessionId().equals(session.getId())) throw bad("Unknown section.");
            if (subjectId == null || !subjects.existsById(subjectId)) throw bad("Unknown subject.");
            RebTeacherAssignment a = existing.get(sectionId + ":" + subjectId);
            if (staffId == null) {
                if (a != null) assignments.delete(a);
                continue;
            }
            if (!staff.existsById(staffId)) throw bad("Unknown teacher.");
            if (a == null) {
                a = new RebTeacherAssignment();
                a.setSessionId(session.getId());
                a.setSectionId(sectionId);
                a.setSubjectId(subjectId);
            }
            a.setStaffId(staffId);
            assignments.save(a);
        }
    }
}
