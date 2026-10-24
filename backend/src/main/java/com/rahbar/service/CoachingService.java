package com.rahbar.service;

import com.rahbar.entity.CoachingStudent;
import com.rahbar.entity.CoachingSubject;
import com.rahbar.entity.CoachingTeacher;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Paging;
import com.rahbar.util.Rows;
import com.rahbar.util.UserRoles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Coaching Center (classes 7 to 10): its students (from the admission form), teachers and the subject list. The
 * office sees every center; a coaching center coordinator or teacher only their own (see {@link CoachingAccess}).
 */
@Service
public class CoachingService {

    public static final List<Integer> CLASSES = List.of(7, 8, 9, 10);
    static final Set<String> STUDENT_STATUSES = Set.of("ACTIVE", "LEFT");
    private static final Pattern MOBILE = Pattern.compile("[6-9][0-9]{9}");

    private final CoachingStudentRepository students;
    private final CoachingTeacherRepository teachers;
    private final CoachingSubjectRepository subjects;
    private final CoachingExamSubjectRepository examSubjects;
    private final CoachingExamMarkRepository marks;
    private final UserRepository users;
    private final RoleRepository roles;
    private final CoachingAccess access;

    public CoachingService(CoachingStudentRepository students, CoachingTeacherRepository teachers, CoachingSubjectRepository subjects,
                           CoachingExamSubjectRepository examSubjects, CoachingExamMarkRepository marks, UserRepository users,
                           RoleRepository roles, CoachingAccess access) {
        this.students = students;
        this.teachers = teachers;
        this.subjects = subjects;
        this.examSubjects = examSubjects;
        this.marks = marks;
        this.users = users;
        this.roles = roles;
        this.access = access;
    }

    /** The centers the user may choose and whether they are limited to their own (every screen sends these). */
    public Map<String, Object> centers() {
        List<Map<String, Object>> list = new ArrayList<>();
        access.centers().forEach(c -> list.add(Map.of("rcc_center_id", c.getRccCenterId(), "center_name", c.getCenterName())));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("centers", list);
        out.put("scoped", access.scoped());
        out.put("classes", CLASSES);
        return out;
    }

    // ---------------------------------------------------------------- students

    /** One page of students (filters optional), with how many active students each class has. */
    public Map<String, Object> students(Long centerId, Integer classLevel, String status, String q, Integer page, Integer size) {
        Set<Long> allowed = access.centerIds();
        Map<Long, String> centerNames = access.names();
        List<CoachingStudent> all = students.findAll().stream().filter(s -> allowed.contains(s.getRccCenterId()))
                .filter(s -> centerId == null || centerId.equals(s.getRccCenterId())).toList();
        Map<Integer, Long> byClass = new TreeMap<>();
        CLASSES.forEach(c -> byClass.put(c, 0L));
        all.stream().filter(s -> "ACTIVE".equals(s.getStatus())).forEach(s -> byClass.merge(s.getClassLevel(), 1L, Long::sum));
        String st = blank(status) ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        List<CoachingStudent> filtered = all.stream()
                .filter(s -> classLevel == null || classLevel.equals(s.getClassLevel()))
                .filter(s -> "ALL".equals(st) || st.equals(s.getStatus()))
                .filter(s -> Paging.matches(q, s.getName(), s.getAdmissionNo(), s.getFatherName(), s.getGuardianMobile(),
                        s.getStudentMobile(), s.getSchoolName()))
                .sorted(Comparator.comparing(CoachingStudent::getClassLevel).thenComparing(CoachingStudent::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>(Paging.of(filtered, page, size, list -> list.stream().map(s -> studentRow(s, centerNames)).toList()));
        out.putAll(centers());
        out.put("by_class", byClass);
        return out;
    }

    static Map<String, Object> studentRow(CoachingStudent s, Map<Long, String> centerNames) {
        Map<String, Object> r = Rows.of(s);
        r.put("center_name", centerNames.get(s.getRccCenterId()));
        return r;
    }

    /** Adds (id null) or updates a student from the admission form. */
    @Transactional
    public Map<String, Object> saveStudent(Long id, Map<String, Object> b) {
        CoachingStudent s = id == null ? new CoachingStudent() : requireStudent(id);
        s.setRccCenterId(access.requireCenter(b.get("rccCenterId")));
        s.setClassLevel(classLevel(b.get("classLevel")));
        s.setName(text(b.get("name"), "Name", 150, true));
        s.setAdmissionNo(text(b.get("admissionNo"), "Admission no.", 30, false));
        s.setAdmissionDate(date(b.get("admissionDate"), "Admission date"));
        s.setGender(gender(b.get("gender")));
        s.setDateOfBirth(date(b.get("dateOfBirth"), "Date of birth"));
        if (s.getDateOfBirth() != null && s.getDateOfBirth().isAfter(LocalDate.now())) throw bad("Date of birth can't be in the future.");
        s.setSchoolName(text(b.get("schoolName"), "School", 200, false));
        s.setPreviousClassPercent(number(b.get("previousClassPercent"), "Previous class marks (%)", new BigDecimal("100")));
        s.setFatherName(text(b.get("fatherName"), "Father's name", 150, false));
        s.setFatherOccupation(text(b.get("fatherOccupation"), "Father's occupation", 100, false));
        s.setMotherName(text(b.get("motherName"), "Mother's name", 150, false));
        s.setGuardianMobile(mobile(b.get("guardianMobile"), "Parent / guardian mobile"));
        s.setStudentMobile(mobile(b.get("studentMobile"), "Student's mobile"));
        s.setEmail(text(b.get("email"), "Email", 150, false));
        if (s.getEmail() != null && !s.getEmail().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw bad("Enter a valid email address.");
        s.setFamilyIncome(number(b.get("familyIncome"), "Family income", null));
        s.setAddress(text(b.get("address"), "Address", 500, false));
        String aadhaar = text(b.get("aadhaarLast4"), "Aadhaar", 4, false);
        if (aadhaar != null && !aadhaar.matches("[0-9]{4}")) throw bad("Aadhaar: only the last 4 digits.");
        s.setAadhaarLast4(aadhaar);
        String status = blank(b.get("status")) ? "ACTIVE" : String.valueOf(b.get("status")).trim().toUpperCase(Locale.ROOT);
        if (!STUDENT_STATUSES.contains(status)) throw bad("Status must be Active or Left.");
        s.setStatus(status);
        s.setRemarks(text(b.get("remarks"), "Remarks", 500, false));
        if (s.getAdmissionNo() != null) {
            students.findAll().stream().filter(o -> !o.getId().equals(s.getId()) && s.getRccCenterId().equals(o.getRccCenterId())
                            && s.getAdmissionNo().equalsIgnoreCase(o.getAdmissionNo())).findFirst()
                    .ifPresent(o -> { throw bad("Admission no. " + s.getAdmissionNo() + " is already " + o.getName() + "'s."); });
        }
        return studentRow(students.save(s), access.names());
    }

    /** Deletes a student entered by mistake; one with exam marks is marked Left instead. */
    @Transactional
    public void deleteStudent(Long id) {
        CoachingStudent s = requireStudent(id);
        if (marks.existsByStudentId(id)) throw bad("This student has exam marks. Set their status to Left instead.");
        students.delete(s);
    }

    CoachingStudent requireStudent(Long id) {
        CoachingStudent s = students.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found."));
        access.requireVisible(s.getRccCenterId(), "Student");
        return s;
    }

    // ---------------------------------------------------------------- teachers

    public Map<String, Object> teachers(Long centerId, String q) {
        Set<Long> allowed = access.centerIds();
        Map<Long, String> centerNames = access.names();
        Map<Long, String> logins = new HashMap<>();
        List<CoachingTeacher> list = teachers.findAll().stream().filter(t -> allowed.contains(t.getRccCenterId()))
                .filter(t -> centerId == null || centerId.equals(t.getRccCenterId()))
                .filter(t -> Paging.matches(q, t.getName(), t.getPhone(), t.getEmail(), t.getSubjects(), t.getQualification()))
                .sorted(Comparator.comparing(CoachingTeacher::isActive).reversed().thenComparing(CoachingTeacher::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        list.stream().map(CoachingTeacher::getUserId).filter(Objects::nonNull).distinct()
                .forEach(uid -> users.findById(uid).ifPresent(u -> logins.put(uid, u.getEmail())));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CoachingTeacher t : list) {
            Map<String, Object> r = Rows.of(t);
            r.put("center_name", centerNames.get(t.getRccCenterId()));
            r.put("login_email", t.getUserId() == null ? null : logins.get(t.getUserId()));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>(centers());
        out.put("rows", rows);
        out.put("subjects", subjects.findAllByOrderBySortOrderAscNameAsc().stream().filter(CoachingSubject::isActive).map(CoachingSubject::getName).toList());
        return out;
    }

    /**
     * Adds or updates a teacher. loginEmail (optional) links the teacher's Rahbar login, which must have the Coaching
     * Teacher role; the login is then tied to the teacher's center so they see only it. Blank unlinks.
     */
    @Transactional
    public Map<String, Object> saveTeacher(Long id, Map<String, Object> b) {
        CoachingTeacher t = id == null ? new CoachingTeacher() : requireTeacher(id);
        t.setRccCenterId(access.requireCenter(b.get("rccCenterId")));
        t.setName(text(b.get("name"), "Name", 150, true));
        t.setGender(gender(b.get("gender")));
        t.setPhone(mobile(b.get("phone"), "Phone"));
        t.setEmail(text(b.get("email"), "Email", 150, false));
        if (t.getEmail() != null && !t.getEmail().contains("@")) throw bad("Enter a valid email address.");
        t.setQualification(text(b.get("qualification"), "Qualification", 150, false));
        t.setSubjects(text(b.get("subjects") instanceof Collection<?> c ? String.join(", ", c.stream().map(String::valueOf).toList()) : b.get("subjects"),
                "Subjects", 300, false));
        t.setClasses(classes(b.get("classes")));
        t.setJoiningDate(date(b.get("joiningDate"), "Joining date"));
        t.setAddress(text(b.get("address"), "Address", 500, false));
        t.setActive(!Boolean.FALSE.equals(b.get("active")) && !"false".equalsIgnoreCase(String.valueOf(b.get("active"))));
        t.setRemarks(text(b.get("remarks"), "Remarks", 500, false));
        if (b.containsKey("loginEmail")) linkLogin(t, text(b.get("loginEmail"), "Login", 150, false));
        teachers.save(t);
        return Map.of("id", t.getId(), "message", "Teacher saved.");
    }

    private void linkLogin(CoachingTeacher t, String email) {
        if (email == null) {
            t.setUserId(null);
            return;
        }
        User u = users.findFirstByEmailIgnoreCaseOrderByIdAsc(email)
                .orElseThrow(() -> bad("No Rahbar user has the email " + email + ". Add the login in Manage Users (role Coaching Teacher) first."));
        Integer teacherRole = roles.findAll().stream().filter(r -> "coaching teacher".equalsIgnoreCase(r.getRoleName()))
                .map(Role::getRoleId).findFirst().orElse(null);
        if (teacherRole == null || !UserRoles.has(u, teacherRole, users)) throw bad(email + " doesn't have the Coaching Teacher role. Give it in Manage Users first.");
        teachers.findByUserId(u.getId()).filter(o -> !o.getId().equals(t.getId()))
                .ifPresent(o -> { throw bad("That login is already linked to " + o.getName() + "."); });
        t.setUserId(u.getId());
        if (!t.getRccCenterId().equals(u.getRccCenterId())) { // the login sees what its center has
            u.setRccCenterId(t.getRccCenterId());
            users.save(u);
        }
    }

    @Transactional
    public void deleteTeacher(Long id) {
        teachers.delete(requireTeacher(id));
    }

    CoachingTeacher requireTeacher(Long id) {
        CoachingTeacher t = teachers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Teacher not found."));
        access.requireVisible(t.getRccCenterId(), "Teacher");
        return t;
    }

    // ---------------------------------------------------------------- subjects

    public List<Map<String, Object>> subjects() {
        return Rows.list(subjects.findAllByOrderBySortOrderAscNameAsc());
    }

    /** Adds or renames a subject (name, sortOrder, active). */
    @Transactional
    public Map<String, Object> saveSubject(Long id, Map<String, Object> b) {
        requireOffice();
        CoachingSubject s = id == null ? new CoachingSubject()
                : subjects.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found."));
        String name = text(b.get("name"), "Subject", 80, true);
        subjects.findByNameIgnoreCase(name).filter(o -> !o.getId().equals(s.getId()))
                .ifPresent(o -> { throw bad("There is already a subject " + o.getName() + "."); });
        s.setName(name);
        if (!blank(b.get("sortOrder"))) s.setSortOrder(ServiceSupport.toInteger(b.get("sortOrder")));
        if (b.containsKey("active")) s.setActive(!"false".equalsIgnoreCase(String.valueOf(b.get("active"))));
        return Rows.of(subjects.save(s));
    }

    /** Deletes a subject no exam uses (otherwise make it inactive). */
    @Transactional
    public void deleteSubject(Long id) {
        requireOffice();
        CoachingSubject s = subjects.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject not found."));
        if (examSubjects.existsBySubjectId(id)) throw bad("Exams use " + s.getName() + ". Make it inactive instead.");
        subjects.delete(s);
    }

    /** The subject list is shared by every center, so only the office (not one center's coordinator) changes it. */
    private void requireOffice() {
        if (access.scoped()) throw com.rahbar.security.Access.forbidden("The subject list is shared by every center; the office changes it.");
    }

    // ---------------------------------------------------------------- reading the forms

    static Integer classLevel(Object v) {
        Integer c = blank(v) ? null : ServiceSupport.toInteger(v);
        if (c == null || !CLASSES.contains(c)) throw bad("Class must be 7, 8, 9 or 10.");
        return c;
    }

    /** "7,8" from a list or a comma-separated text; only 7 to 10. */
    static String classes(Object v) {
        if (blank(v)) return null;
        Collection<?> items = v instanceof Collection<?> c ? c : Arrays.asList(String.valueOf(v).split(","));
        TreeSet<Integer> out = new TreeSet<>();
        for (Object item : items) if (!blank(item)) out.add(classLevel(String.valueOf(item).trim()));
        return out.isEmpty() ? null : String.join(",", out.stream().map(String::valueOf).toList());
    }

    static String text(Object v, String label, int max, boolean required) {
        String s = v == null ? null : String.valueOf(v).trim();
        if (s == null || s.isEmpty()) {
            if (required) throw bad(label + " is required.");
            return null;
        }
        if (s.length() > max) throw bad(label + " can be at most " + max + " characters.");
        return s;
    }

    static String gender(Object v) {
        if (blank(v)) return null;
        String g = String.valueOf(v).trim().toUpperCase(Locale.ROOT);
        if (!g.equals("M") && !g.equals("F")) throw bad("Gender must be Male or Female.");
        return g;
    }

    static String mobile(Object v, String label) {
        if (blank(v)) return null;
        String digits = String.valueOf(v).replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (!MOBILE.matcher(digits).matches()) throw bad(label + ": a 10-digit mobile number.");
        return digits;
    }

    static LocalDate date(Object v, String label) {
        if (blank(v)) return null;
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (Exception e) {
            throw bad(label + " must be a date like 2026-07-01.");
        }
    }

    static BigDecimal number(Object v, String label, BigDecimal max) {
        if (blank(v)) return null;
        try {
            BigDecimal d = new BigDecimal(String.valueOf(v).trim().replace(",", ""));
            if (d.signum() < 0 || (max != null && d.compareTo(max) > 0)) throw bad(label + " must be between 0 and " + (max == null ? "any amount" : max) + ".");
            return d;
        } catch (NumberFormatException e) {
            throw bad(label + " must be a number.");
        }
    }

    static boolean blank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
