package com.rahbar.service;

import com.rahbar.entity.RebClass;
import com.rahbar.repository.RebClassRepository;
import com.rahbar.entity.RebEnrollment;
import com.rahbar.repository.RebEnrollmentRepository;
import com.rahbar.entity.RebGuardian;
import com.rahbar.repository.RebGuardianRepository;
import com.rahbar.entity.RebSection;
import com.rahbar.repository.RebSectionRepository;
import com.rahbar.entity.RebSession;
import com.rahbar.repository.RebSessionRepository;
import com.rahbar.entity.RebStudent;
import com.rahbar.entity.RebStudentDocument;
import com.rahbar.repository.RebStudentDocumentRepository;
import com.rahbar.repository.RebStudentRepository;

import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.service.FileStorageService;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * The girls of REB: admission (with guardians and a section), profile, guardians, documents, enrollment per
 * session, leaving / passing out, and year-end promotion. Private details (Aadhaar, photo, documents, family income)
 * are only sent to people with "REB: private details".
 */
@Service
public class RebStudentService {

    static final Set<String> FEE_PLANS = Set.of("SELF", "SPONSORED", "FREE");
    static final Set<String> STATUSES = Set.of("ACTIVE", "LEFT", "PASSED_OUT");
    static final Set<String> RELATIONS = Set.of("FATHER", "MOTHER", "BROTHER", "SISTER", "UNCLE", "AUNT", "GRANDPARENT", "GUARDIAN");
    static final Set<String> CATEGORIES = Set.of("GEN", "EWS", "BC", "EBC", "SC", "ST");
    static final Set<String> DOCUMENT_KINDS = Set.of("BIRTH_CERTIFICATE", "AADHAAR", "PHOTO", "TC", "MARKSHEET", "OTHER");
    static final long MAX_DOCUMENT = 5L * 1024 * 1024;

    private final RebStudentRepository students;
    private final RebGuardianRepository guardians;
    private final RebEnrollmentRepository enrollments;
    private final RebStudentDocumentRepository documents;
    private final RebSectionRepository sections;
    private final RebClassRepository classes;
    private final RebSessionRepository sessions;
    private final RebSetupService setup;
    private final FileStorageService files;
    private final UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.security.RebClassScope classScope;

    /** The signed-in user's REB classes (everything when not limited to the classes she teaches). */
    com.rahbar.security.RebClassScope.Scope classScope() {
        return classScope == null ? new com.rahbar.security.RebClassScope.Scope(true, Set.of(), Set.of()) : classScope.current();
    }

    public RebStudentService(RebStudentRepository students, RebGuardianRepository guardians, RebEnrollmentRepository enrollments,
                             RebStudentDocumentRepository documents, RebSectionRepository sections, RebClassRepository classes,
                             RebSessionRepository sessions, RebSetupService setup, FileStorageService files, UserRepository users) {
        this.students = students;
        this.guardians = guardians;
        this.enrollments = enrollments;
        this.documents = documents;
        this.sections = sections;
        this.classes = classes;
        this.sessions = sessions;
        this.setup = setup;
        this.files = files;
        this.users = users;
    }

    static boolean seesPrivate() {
        return Access.can(Section.REB_STUDENT_PRIVATE, Section.Level.VIEW);
    }

    static boolean editsPrivate() {
        return Access.can(Section.REB_STUDENT_PRIVATE, Section.Level.EDIT);
    }

    RebStudent require(Long id) {
        return students.findById(id).orElseThrow(() -> notFound("Student"));
    }

    // ---------------------------------------------------------------- list

    /** Section labels ("Class 6 A") by section id. */
    Map<Long, String> sectionLabels() {
        Map<Long, RebClass> classById = new HashMap<>();
        classes.findAll().forEach(c -> classById.put(c.getId(), c));
        Map<Long, String> out = new HashMap<>();
        sections.findAll().forEach(s -> out.put(s.getId(), RebSetupService.label(classById.get(s.getClassId()), s)));
        return out;
    }

    /**
     * Girls with their section in the session (default: the current one) and their main guardian. Filters: classId,
     * sectionId, status (default ACTIVE; ALL for everyone), residential, feePlan, q (name, admission number, guardian
     * name or phone).
     */
    public Map<String, Object> list(Long sessionId, Long classId, Long sectionId, String status, Boolean residential, String feePlan, String q) {
        RebSession session = sessionId != null ? setup.session(sessionId) : sessions.findFirstByCurrentTrue().orElse(null);
        Map<Long, RebEnrollment> enrolled = new HashMap<>();
        if (session != null) enrollments.findBySessionId(session.getId()).forEach(e -> enrolled.put(e.getStudentId(), e));
        Map<Long, RebSection> sectionById = new HashMap<>();
        sections.findAll().forEach(s -> sectionById.put(s.getId(), s));
        Map<Long, String> labels = sectionLabels();
        Map<Long, RebGuardian> mainGuardian = new HashMap<>();
        for (RebGuardian g : guardians.findAll()) {
            RebGuardian cur = mainGuardian.get(g.getStudentId());
            if (cur == null || (g.isPrimary() && !cur.isPrimary())) mainGuardian.put(g.getStudentId(), g);
        }
        String query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        String wanted = status == null || status.isBlank() ? "ACTIVE" : status.toUpperCase(Locale.ROOT);
        com.rahbar.security.RebClassScope.Scope scope = classScope();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebStudent s : students.findAll()) {
            if (!"ALL".equals(wanted) && !wanted.equals(s.getStatus())) continue;
            if (residential != null && residential != s.isResidential()) continue;
            if (feePlan != null && !feePlan.isBlank() && !feePlan.equalsIgnoreCase(s.getFeePlan())) continue;
            RebEnrollment e = enrolled.get(s.getId());
            RebSection sec = e == null ? null : sectionById.get(e.getSectionId());
            if (sectionId != null && (e == null || !sectionId.equals(e.getSectionId()))) continue;
            if (!scope.all() && (e == null || !scope.seesSection(e.getSectionId()))) continue; // a teacher: her classes only
            if (classId != null && (sec == null || !classId.equals(sec.getClassId()))) continue;
            RebGuardian g = mainGuardian.get(s.getId());
            if (!query.isEmpty() && java.util.stream.Stream.of(s.getName(), s.getAdmissionNo(), g == null ? null : g.getName(), g == null ? null : g.getPhone())
                    .noneMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(query))) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", s.getId());
            r.put("admission_no", s.getAdmissionNo());
            r.put("name", s.getName());
            r.put("date_of_birth", s.getDateOfBirth());
            r.put("section_id", e == null ? null : e.getSectionId());
            r.put("class_id", sec == null ? null : sec.getClassId());
            r.put("section", e == null ? null : labels.get(e.getSectionId()));
            r.put("roll_no", e == null ? null : e.getRollNo());
            r.put("residential", s.isResidential());
            r.put("fee_plan", s.getFeePlan());
            r.put("status", s.getStatus());
            r.put("guardian_name", g == null ? null : g.getName());
            r.put("guardian_relation", g == null ? null : g.getRelation());
            r.put("guardian_phone", g == null ? null : g.getPhone());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("section") == null ? "~" : r.get("section")))
                .thenComparing(r -> rollOrder(r.get("roll_no")))
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", session == null ? null : Rows.of(session));
        out.put("students", rows);
        return out;
    }

    private static int rollOrder(Object roll) {
        try {
            return roll == null ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(roll).replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    // ---------------------------------------------------------------- one girl

    public Map<String, Object> get(Long id) {
        RebStudent s = require(id);
        com.rahbar.security.RebClassScope.Scope scope = classScope();
        if (!scope.all() && enrollments.findByStudentId(id).stream().noneMatch(e -> scope.seesSection(e.getSectionId()))) {
            throw notFound("Student");
        }
        boolean priv = seesPrivate();
        Map<String, Object> student = Rows.of(s);
        if (!priv) {
            student.remove("aadhaar_last4");
            student.remove("photo_path");
        }
        List<Map<String, Object>> guardianRows = new ArrayList<>();
        for (RebGuardian g : guardians.findByStudentIdOrderByPrimaryDescIdAsc(id)) {
            Map<String, Object> r = Rows.of(g);
            if (!priv) r.remove("monthly_income");
            guardianRows.add(r);
        }
        Map<Long, String> labels = sectionLabels();
        Map<Long, RebSession> sessionById = new HashMap<>();
        sessions.findAll().forEach(x -> sessionById.put(x.getId(), x));
        List<Map<String, Object>> history = new ArrayList<>();
        List<RebEnrollment> list = new ArrayList<>(enrollments.findByStudentId(id));
        list.sort(Comparator.comparing((RebEnrollment e) -> sessionById.get(e.getSessionId()) == null ? LocalDate.MIN
                : sessionById.get(e.getSessionId()).getStartDate()).reversed());
        for (RebEnrollment e : list) {
            Map<String, Object> r = Rows.of(e);
            RebSession ses = sessionById.get(e.getSessionId());
            r.put("session_name", ses == null ? null : ses.getName());
            r.put("session_current", ses != null && ses.isCurrent());
            r.put("section", labels.get(e.getSectionId()));
            history.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", student);
        out.put("guardians", guardianRows);
        out.put("enrollments", history);
        out.put("documents", priv ? Rows.list(documents.findByStudentIdOrderByIdDesc(id)) : null);
        out.put("rahbar_user", s.getRahbarUserId() == null ? null
                : users.findById(s.getRahbarUserId()).map(u -> Rows.pick(u, "id", "user_id", "name")).orElse(null));
        out.put("private", priv);
        return out;
    }

    // ---------------------------------------------------------------- admission

    /**
     * Admits a girl: her details, guardians (at least one) and section. Body: the student fields (see readStudent),
     * sectionId, rollNo, admissionNo (optional: generated as PREFIX/YEAR/0001), guardians: [{relation, name, phone,
     * whatsapp, email, occupation, monthlyIncome, primary, mayPickUp}]. Returns the new girl's id.
     */
    @Transactional
    public Long admit(Map<String, Object> body) {
        RebSection section = requireSection(id(body.get("sectionId")));
        RebStudent s = new RebStudent();
        readStudent(s, body, true);
        if (s.getAdmittedOn() == null) s.setAdmittedOn(LocalDate.now());
        String no = text(body.get("admissionNo"), "Admission number", 30, false);
        if (no != null && students.findByAdmissionNo(no).isPresent()) throw bad("Admission number " + no + " is already used.");
        s.setAdmissionNo(no != null ? no : nextAdmissionNo(s.getAdmittedOn()));
        students.save(s);

        List<?> list = body.get("guardians") instanceof List<?> l ? l : List.of();
        if (list.isEmpty()) throw bad("Add at least one parent or guardian.");
        boolean primarySet = false;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid guardian.");
            @SuppressWarnings("unchecked") Map<String, Object> gm = (Map<String, Object>) m;
            RebGuardian g = new RebGuardian();
            g.setStudentId(s.getId());
            readGuardian(g, gm);
            if (g.isPrimary()) {
                if (primarySet) g.setPrimary(false);
                primarySet = true;
            }
            guardians.save(g);
        }
        if (!primarySet) {
            List<RebGuardian> saved = guardians.findByStudentIdOrderByPrimaryDescIdAsc(s.getId());
            saved.get(0).setPrimary(true);
            guardians.save(saved.get(0));
        }
        enroll(s.getId(), section, text(body.get("rollNo"), "Roll number", 10, false));
        return s.getId();
    }

    /** The next admission number for the year: PREFIX/2026/0001, PREFIX/2026/0002, ... */
    String nextAdmissionNo(LocalDate admittedOn) {
        String prefix = setup.setting("admission_prefix", "REB") + "/" + admittedOn.getYear() + "/";
        int max = 0;
        for (String n : students.findAdmissionNosStartingWith(prefix)) {
            try {
                max = Math.max(max, Integer.parseInt(n.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // a number typed by hand in another form
            }
        }
        return prefix + String.format("%04d", max + 1);
    }

    /** Copies the student fields from a form. Private fields (Aadhaar) only with "REB: private details" (edit). */
    void readStudent(RebStudent s, Map<String, Object> body, boolean creating) {
        if (creating || body.containsKey("name")) s.setName(text(body.get("name"), "Name", 120, true));
        if (creating || body.containsKey("dateOfBirth")) {
            s.setDateOfBirth(date(body.get("dateOfBirth"), "Date of birth"));
            if (s.getDateOfBirth() != null && s.getDateOfBirth().isAfter(LocalDate.now())) throw bad("The date of birth is in the future.");
        }
        if (creating || body.containsKey("bloodGroup")) s.setBloodGroup(text(body.get("bloodGroup"), "Blood group", 5, false));
        if (creating || body.containsKey("address")) s.setAddress(text(body.get("address"), "Address", 500, false));
        if (creating || body.containsKey("district")) s.setDistrict(text(body.get("district"), "District", 80, false));
        if (creating || body.containsKey("state")) s.setState(text(body.get("state"), "State", 40, false));
        if (creating || body.containsKey("socialCategory")) s.setSocialCategory(code(body.get("socialCategory"), CATEGORIES, null, "category"));
        if (creating || body.containsKey("boardRegistrationNo")) s.setBoardRegistrationNo(text(body.get("boardRegistrationNo"), "Board registration number", 40, false));
        if (creating || body.containsKey("previousSchool")) s.setPreviousSchool(text(body.get("previousSchool"), "Previous school", 150, false));
        if (creating || body.containsKey("residential")) s.setResidential(flag(body.get("residential"), true));
        if (creating || body.containsKey("feePlan")) s.setFeePlan(code(body.get("feePlan"), FEE_PLANS, "SELF", "fee plan"));
        if (creating || body.containsKey("admittedOn")) s.setAdmittedOn(date(body.get("admittedOn"), "Admitted on"));
        if (creating || body.containsKey("notes")) s.setNotes(text(body.get("notes"), "Notes", 2000, false));
        if (body.containsKey("aadhaarLast4") && editsPrivate()) {
            String a = text(body.get("aadhaarLast4"), "Aadhaar", 14, false);
            if (a != null) {
                String digits = a.replaceAll("\\D", "");
                if (digits.length() != 4 && digits.length() != 12) throw bad("Aadhaar: enter the last 4 digits (or the full 12-digit number; only the last 4 are kept).");
                a = digits.substring(digits.length() - 4);
            }
            s.setAadhaarLast4(a);
        }
    }

    void readGuardian(RebGuardian g, Map<String, Object> m) {
        g.setRelation(code(m.get("relation"), RELATIONS, "FATHER", "relation"));
        g.setName(text(m.get("name"), "Guardian's name", 120, true));
        g.setPhone(phone(m.get("phone"), "Guardian's phone"));
        g.setWhatsapp(phone(m.get("whatsapp"), "WhatsApp"));
        g.setEmail(text(m.get("email"), "Guardian's email", 150, false));
        g.setOccupation(text(m.get("occupation"), "Occupation", 120, false));
        if (m.containsKey("monthlyIncome") && editsPrivate()) g.setMonthlyIncome(money(m.get("monthlyIncome"), "Monthly income"));
        g.setPrimary(flag(m.get("primary"), false));
        g.setMayPickUp(flag(m.get("mayPickUp"), true));
    }

    RebSection requireSection(Long sectionId) {
        if (sectionId == null) throw bad("Choose the class and section.");
        return sections.findById(sectionId).orElseThrow(() -> bad("Unknown section."));
    }

    /** Puts the girl in a section for that section's session (moving her if she already has one there). */
    RebEnrollment enroll(Long studentId, RebSection section, String rollNo) {
        RebEnrollment e = enrollments.findByStudentIdAndSessionId(studentId, section.getSessionId()).orElseGet(RebEnrollment::new);
        e.setStudentId(studentId);
        e.setSessionId(section.getSessionId());
        e.setSectionId(section.getId());
        e.setRollNo(rollNo);
        if (e.getStatus() == null) e.setStatus("ACTIVE");
        return enrollments.save(e);
    }

    @Transactional
    public void update(Long id, Map<String, Object> body) {
        RebStudent s = require(id);
        readStudent(s, body, false);
        students.save(s);
    }

    /** Body: { sectionId, rollNo }: moves her to another section (of that section's session) or sets the roll number. */
    @Transactional
    public void setEnrollment(Long id, Map<String, Object> body) {
        RebStudent s = require(id);
        RebSection section = requireSection(id(body.get("sectionId")));
        RebEnrollment e = enroll(s.getId(), section, text(body.get("rollNo"), "Roll number", 10, false));
        if ("ACTIVE".equals(s.getStatus()) && !"ACTIVE".equals(e.getStatus()) && isCurrent(section.getSessionId())) {
            e.setStatus("ACTIVE");
            enrollments.save(e);
        }
    }

    private boolean isCurrent(Long sessionId) {
        return sessions.findById(sessionId).map(RebSession::isCurrent).orElse(false);
    }

    /**
     * Body: { status: LEFT | PASSED_OUT | ACTIVE, date, reason }. Leaving / passing out also closes her enrollment in
     * the current session; ACTIVE takes her back.
     */
    @Transactional
    public void setStatus(Long id, Map<String, Object> body) {
        RebStudent s = require(id);
        String status = code(body.get("status"), STATUSES, null, "status");
        if (status == null) throw bad("Choose the new status.");
        LocalDate day = date(body.get("date"), "Date");
        s.setStatus(status);
        if ("ACTIVE".equals(status)) {
            s.setLeftOn(null);
            s.setLeavingReason(null);
        } else {
            s.setLeftOn(day == null ? LocalDate.now() : day);
            s.setLeavingReason(text(body.get("reason"), "Reason", 500, "LEFT".equals(status)));
        }
        students.save(s);
        sessions.findFirstByCurrentTrue()
                .flatMap(cur -> enrollments.findByStudentIdAndSessionId(id, cur.getId()))
                .ifPresent(e -> {
                    e.setStatus(switch (status) { case "LEFT" -> "LEFT"; case "PASSED_OUT" -> "COMPLETED"; default -> "ACTIVE"; });
                    enrollments.save(e);
                });
    }

    // ---------------------------------------------------------------- guardians

    @Transactional
    public RebGuardian addGuardian(Long studentId, Map<String, Object> body) {
        require(studentId);
        RebGuardian g = new RebGuardian();
        g.setStudentId(studentId);
        readGuardian(g, body);
        if (guardians.findByStudentIdOrderByPrimaryDescIdAsc(studentId).isEmpty()) g.setPrimary(true);
        return savePrimary(guardians.save(g));
    }

    @Transactional
    public RebGuardian updateGuardian(Long guardianId, Map<String, Object> body) {
        RebGuardian g = guardians.findById(guardianId).orElseThrow(() -> notFound("Guardian"));
        readGuardian(g, body);
        return savePrimary(guardians.save(g));
    }

    /** Only one main guardian per girl. */
    private RebGuardian savePrimary(RebGuardian g) {
        if (!g.isPrimary()) return g;
        for (RebGuardian other : guardians.findByStudentIdOrderByPrimaryDescIdAsc(g.getStudentId())) {
            if (!other.getId().equals(g.getId()) && other.isPrimary()) {
                other.setPrimary(false);
                guardians.save(other);
            }
        }
        return g;
    }

    @Transactional
    public void deleteGuardian(Long guardianId) {
        RebGuardian g = guardians.findById(guardianId).orElseThrow(() -> notFound("Guardian"));
        List<RebGuardian> all = guardians.findByStudentIdOrderByPrimaryDescIdAsc(g.getStudentId());
        if (all.size() <= 1) throw bad("A girl must have at least one parent or guardian.");
        guardians.delete(g);
        if (g.isPrimary()) {
            RebGuardian next = all.stream().filter(x -> !x.getId().equals(g.getId())).findFirst().orElseThrow();
            next.setPrimary(true);
            guardians.save(next);
        }
    }

    // ---------------------------------------------------------------- documents

    @Transactional
    public Map<String, Object> addDocument(Long studentId, String kind, MultipartFile file) {
        RebStudent s = require(studentId);
        String k = code(kind, DOCUMENT_KINDS, null, "document type");
        if (k == null) throw bad("Choose the type of document.");
        if (file == null || file.isEmpty()) throw bad("Choose a file.");
        if (file.getSize() > MAX_DOCUMENT) throw bad("The file is too large (at most 5 MB).");
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!type.startsWith("image/") && !type.equals("application/pdf")) throw bad("Upload a photo (JPG / PNG) or a PDF.");
        if ("PHOTO".equals(k) && !type.startsWith("image/")) throw bad("The photo must be an image.");
        String original = files.sanitizeFilename(file.getOriginalFilename());
        String stored = files.store(file, "reb_" + studentId + "_" + System.currentTimeMillis() + "_" + original);
        RebStudentDocument d = new RebStudentDocument();
        d.setStudentId(studentId);
        d.setKind(k);
        d.setFilePath(stored);
        d.setFileName(text(file.getOriginalFilename(), "File name", 200, false));
        documents.save(d);
        if ("PHOTO".equals(k)) {
            s.setPhotoPath(stored);
            students.save(s);
        }
        return Rows.of(d);
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        RebStudentDocument d = documents.findById(documentId).orElseThrow(() -> notFound("Document"));
        documents.delete(d);
        students.findById(d.getStudentId()).filter(s -> d.getFilePath().equals(s.getPhotoPath())).ifPresent(s -> {
            s.setPhotoPath(null);
            students.save(s);
        });
        try {
            java.nio.file.Files.deleteIfExists(files.resolve(d.getFilePath()));
        } catch (java.io.IOException ignored) {
            // the row is gone; a leftover file can't be opened by anyone (FileAccessService checks the row)
        }
    }

    // ---------------------------------------------------------------- promotion

    /**
     * Year end, for every girl active in the "from" session: PROMOTE to the next class (its next_class_id, else the
     * next active class by order), COMPLETE when there is no next class, with the section of that class in the "to"
     * session (the first one when there are several). Girls already enrolled in the "to" session are shown as done.
     */
    public Map<String, Object> promotionPreview(Long fromSessionId, Long toSessionId) {
        RebSession from = setup.session(fromSessionId);
        RebSession to = setup.session(toSessionId);
        if (from.getId().equals(to.getId())) throw bad("Choose two different sessions.");
        if (!to.getStartDate().isAfter(from.getStartDate())) throw bad("Promote into a later session.");
        List<RebClass> classList = classes.findAllByOrderByLevelOrderAscNameAsc();
        Map<Long, RebClass> classById = new HashMap<>();
        classList.forEach(c -> classById.put(c.getId(), c));
        Map<Long, RebSection> sectionById = new HashMap<>();
        sections.findAll().forEach(s -> sectionById.put(s.getId(), s));
        Map<Long, String> labels = sectionLabels();
        Map<Long, List<RebSection>> targetSections = new HashMap<>();
        for (RebSection s : sections.findBySessionId(to.getId())) targetSections.computeIfAbsent(s.getClassId(), k -> new ArrayList<>()).add(s);
        targetSections.values().forEach(l -> l.sort(Comparator.comparing(RebSection::getName)));
        Set<Long> alreadyThere = new HashSet<>();
        enrollments.findBySessionId(to.getId()).forEach(e -> alreadyThere.add(e.getStudentId()));
        Map<Long, RebStudent> studentById = new HashMap<>();
        students.findAll().forEach(s -> studentById.put(s.getId(), s));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebEnrollment e : enrollments.findBySessionId(from.getId())) {
            RebStudent s = studentById.get(e.getStudentId());
            if (s == null || !"ACTIVE".equals(s.getStatus()) || !"ACTIVE".equals(e.getStatus())) continue;
            RebSection sec = sectionById.get(e.getSectionId());
            RebClass cls = sec == null ? null : classById.get(sec.getClassId());
            RebClass next = nextClass(cls, classList, classById);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("enrollment_id", e.getId());
            r.put("student_id", s.getId());
            r.put("admission_no", s.getAdmissionNo());
            r.put("name", s.getName());
            r.put("from_section", labels.get(e.getSectionId()));
            r.put("from_section_id", e.getSectionId());
            r.put("done", alreadyThere.contains(s.getId()));
            if (next == null) {
                r.put("action", "COMPLETE");
                r.put("target_section_id", null);
            } else {
                List<RebSection> options = targetSections.getOrDefault(next.getId(), List.of());
                r.put("action", "PROMOTE");
                r.put("next_class", next.getName());
                r.put("target_section_id", options.isEmpty() ? null : options.get(0).getId());
            }
            // the same class in the new session, for girls who stay back
            List<RebSection> same = cls == null ? List.of() : targetSections.getOrDefault(cls.getId(), List.of());
            r.put("detain_section_id", same.isEmpty() ? null : same.get(0).getId());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("from_section")))
                .thenComparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        List<Map<String, Object>> targets = setup.sections(to.getId()).stream()
                .map(x -> Map.<String, Object>of("id", x.get("id"), "label", x.get("label"))).toList();
        return Map.of("from", Rows.of(from), "to", Rows.of(to), "rows", rows, "sections", targets);
    }

    static RebClass nextClass(RebClass cls, List<RebClass> ordered, Map<Long, RebClass> byId) {
        if (cls == null) return null;
        if (cls.getNextClassId() != null) return byId.get(cls.getNextClassId());
        boolean after = false;
        for (RebClass c : ordered) {
            if (after && c.isActive() && c.getLevelOrder() > cls.getLevelOrder()) return c;
            if (c.getId().equals(cls.getId())) after = true;
        }
        return null;
    }

    /**
     * Applies the promotion. Body: { fromSessionId, toSessionId, rows: [{ enrollmentId, action: PROMOTE | DETAIN |
     * COMPLETE | LEAVE | SKIP, targetSectionId }] }. Girls already enrolled in the "to" session are left alone, so it
     * can be run again. Returns how many girls were moved.
     */
    @Transactional
    public Map<String, Object> promote(Map<String, Object> body) {
        RebSession from = setup.session(id(body.get("fromSessionId")));
        RebSession to = setup.session(id(body.get("toSessionId")));
        if (from.getId().equals(to.getId())) throw bad("Choose two different sessions.");
        if (!(body.get("rows") instanceof List<?> list)) throw bad("Send the list of girls.");
        int promoted = 0, detained = 0, completed = 0, left = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid row.");
            RebEnrollment e = enrollments.findById(id(m.get("enrollmentId"))).orElseThrow(() -> bad("Unknown enrollment."));
            if (!e.getSessionId().equals(from.getId())) throw bad("That girl is not in the session being closed.");
            String action = code(m.get("action"), Set.of("PROMOTE", "DETAIN", "COMPLETE", "LEAVE", "SKIP"), "SKIP", "action");
            if ("SKIP".equals(action) || enrollments.findByStudentIdAndSessionId(e.getStudentId(), to.getId()).isPresent()) continue;
            RebStudent s = require(e.getStudentId());
            switch (action) {
                case "PROMOTE", "DETAIN" -> {
                    RebSection target = requireSection(id(m.get("targetSectionId")));
                    if (!target.getSessionId().equals(to.getId())) throw bad("Choose a section of " + to.getName() + " for " + s.getName() + ".");
                    e.setStatus("PROMOTE".equals(action) ? "PROMOTED" : "DETAINED");
                    enrollments.save(e);
                    RebEnrollment n = new RebEnrollment();
                    n.setStudentId(s.getId());
                    n.setSessionId(to.getId());
                    n.setSectionId(target.getId());
                    n.setRollNo(null);
                    enrollments.save(n);
                    if ("PROMOTE".equals(action)) promoted++; else detained++;
                }
                case "COMPLETE" -> {
                    e.setStatus("COMPLETED");
                    enrollments.save(e);
                    s.setStatus("PASSED_OUT");
                    s.setLeftOn(from.getEndDate());
                    students.save(s);
                    completed++;
                }
                case "LEAVE" -> {
                    e.setStatus("LEFT");
                    enrollments.save(e);
                    s.setStatus("LEFT");
                    s.setLeftOn(from.getEndDate());
                    s.setLeavingReason("Did not continue after " + from.getName() + ".");
                    students.save(s);
                    left++;
                }
                default -> { }
            }
        }
        return Map.of("promoted", promoted, "detained", detained, "completed", completed, "left", left,
                "message", promoted + " promoted, " + detained + " kept in the same class, " + completed + " passed out, " + left + " left.");
    }

    // ---------------------------------------------------------------- dashboard

    /** The current session at a glance. */
    public Map<String, Object> dashboard() {
        RebSession session = sessions.findFirstByCurrentTrue().orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", session == null ? null : Rows.of(session));
        List<RebStudent> all = students.findAll();
        List<RebStudent> active = all.stream().filter(s -> "ACTIVE".equals(s.getStatus())).toList();
        out.put("active", active.size());
        out.put("residential", active.stream().filter(RebStudent::isResidential).count());
        Map<String, Long> plans = new LinkedHashMap<>();
        for (String p : List.of("SELF", "SPONSORED", "FREE")) plans.put(p, active.stream().filter(s -> p.equals(s.getFeePlan())).count());
        out.put("fee_plans", plans);
        if (session != null) {
            out.put("admitted_this_session", all.stream().filter(s -> s.getAdmittedOn() != null
                    && !s.getAdmittedOn().isBefore(session.getStartDate()) && !s.getAdmittedOn().isAfter(session.getEndDate())).count());
            out.put("left_this_session", all.stream().filter(s -> s.getLeftOn() != null && "LEFT".equals(s.getStatus())
                    && !s.getLeftOn().isBefore(session.getStartDate()) && !s.getLeftOn().isAfter(session.getEndDate())).count());
            List<Map<String, Object>> bySection = setup.sections(session.getId()).stream()
                    .map(x -> Map.<String, Object>of("id", x.get("id"), "label", x.get("label"), "girls", x.get("girls"),
                            "capacity", x.get("capacity") == null ? 0 : x.get("capacity"),
                            "class_teacher", x.get("class_teacher_name") == null ? "" : x.get("class_teacher_name")))
                    .toList();
            out.put("sections", bySection);
            Set<Long> enrolledActive = new HashSet<>();
            enrollments.findBySessionId(session.getId()).stream().filter(e -> "ACTIVE".equals(e.getStatus())).forEach(e -> enrolledActive.add(e.getStudentId()));
            out.put("not_enrolled", active.stream().filter(s -> !enrolledActive.contains(s.getId())).count());
            out.put("sections_without_teacher", bySection.stream().filter(x -> "".equals(x.get("class_teacher"))).count());
        }
        Set<Long> withGuardianPhone = new HashSet<>();
        guardians.findAll().stream().filter(g -> g.getPhone() != null && !g.getPhone().isBlank()).forEach(g -> withGuardianPhone.add(g.getStudentId()));
        out.put("without_guardian_phone", active.stream().filter(s -> !withGuardianPhone.contains(s.getId())).count());
        if (seesPrivate()) {
            Set<Long> withBirthCert = new HashSet<>();
            documents.findAll().stream().filter(d -> "BIRTH_CERTIFICATE".equals(d.getKind())).forEach(d -> withBirthCert.add(d.getStudentId()));
            out.put("without_birth_certificate", active.stream().filter(s -> !withBirthCert.contains(s.getId())).count());
        }
        return out;
    }
}
