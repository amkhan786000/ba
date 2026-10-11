package com.rahbar.reb;

import com.rahbar.service.ReportService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

import static com.rahbar.reb.RebSupport.*;

/**
 * REB reports (Rahbar-e-Banaat > Reports), viewed on screen or downloaded as CSV / Excel / PDF through Rahbar's
 * ReportService. They describe the current session unless a date range is given.
 */
@Service
public class RebReportService {

    public record Definition(String key, String category, String title, String description, boolean dateRange) {}

    static final List<Definition> CATALOG = List.of(
            new Definition("reb_strength", "Students", "Strength by class",
                    "Girls in each section of the current session: residential, day scholars and fee plans.", false),
            new Definition("reb_students", "Students", "List of girls",
                    "Every girl studying now with class, roll number, date of birth and fee plan.", false),
            new Definition("reb_admissions_leavers", "Students", "Admissions and leavers",
                    "Girls admitted, and girls who left or passed out, in the date range (default: the current session).", true),
            new Definition("reb_guardian_contacts", "Students", "Guardian contacts",
                    "Class lists with the parents' and guardians' names and phone numbers.", false),
            new Definition("reb_board_candidates", "Students", "Board exam candidates",
                    "Girls in the Bihar Board exam class(es) with their board registration details.", false),
            new Definition("reb_staff", "Staff", "Teachers and subjects",
                    "Every staff member with the sections and subjects they teach in the current session.", false));

    private final RebStudentRepository students;
    private final RebGuardianRepository guardians;
    private final RebEnrollmentRepository enrollments;
    private final RebSectionRepository sections;
    private final RebClassRepository classes;
    private final RebStaffRepository staff;
    private final RebTeacherAssignmentRepository assignments;
    private final RebSubjectRepository subjects;
    private final RebSetupService setup;
    private final RebStudentService studentService;
    private final ReportService reportService;

    public RebReportService(RebStudentRepository students, RebGuardianRepository guardians, RebEnrollmentRepository enrollments,
                            RebSectionRepository sections, RebClassRepository classes, RebStaffRepository staff,
                            RebTeacherAssignmentRepository assignments, RebSubjectRepository subjects, RebSetupService setup,
                            RebStudentService studentService, ReportService reportService) {
        this.students = students;
        this.guardians = guardians;
        this.enrollments = enrollments;
        this.sections = sections;
        this.classes = classes;
        this.staff = staff;
        this.assignments = assignments;
        this.subjects = subjects;
        this.setup = setup;
        this.studentService = studentService;
        this.reportService = reportService;
    }

    public List<Definition> catalog() {
        return CATALOG;
    }

    private static Definition definition(String key) {
        return CATALOG.stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow(() -> bad("Unknown report."));
    }

    public Map<String, Object> data(String key, LocalDate from, LocalDate to) {
        Definition def = definition(key);
        List<Map<String, Object>> rows = rows(key, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("title", def.title());
        body.put("columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
        body.put("rows", rows);
        return body;
    }

    public ReportService.Report build(String key, String format, LocalDate from, LocalDate to) {
        Definition def = definition(key);
        return reportService.build(rows(key, from, to), key + "_report", format, def.title());
    }

    // ---------------------------------------------------------------- rows

    /** Active enrollments of the current session, sorted by section label then roll number. */
    private record Seat(RebStudent student, RebEnrollment enrollment, RebSection section, RebClass cls, String label) {}

    private List<Seat> seats() {
        RebSession session = setup.currentSession();
        Map<Long, RebStudent> byId = new HashMap<>();
        students.findAll().forEach(s -> byId.put(s.getId(), s));
        Map<Long, RebSection> sectionById = new HashMap<>();
        sections.findAll().forEach(s -> sectionById.put(s.getId(), s));
        Map<Long, RebClass> classById = new HashMap<>();
        classes.findAll().forEach(c -> classById.put(c.getId(), c));
        List<Seat> out = new ArrayList<>();
        for (RebEnrollment e : enrollments.findBySessionId(session.getId())) {
            RebStudent s = byId.get(e.getStudentId());
            if (s == null || !"ACTIVE".equals(s.getStatus()) || !"ACTIVE".equals(e.getStatus())) continue;
            RebSection sec = sectionById.get(e.getSectionId());
            RebClass c = sec == null ? null : classById.get(sec.getClassId());
            out.add(new Seat(s, e, sec, c, RebSetupService.label(c, sec)));
        }
        out.sort(Comparator.comparing((Seat x) -> x.cls() == null ? 0 : x.cls().getLevelOrder())
                .thenComparing(Seat::label)
                .thenComparing(x -> roll(x.enrollment().getRollNo()))
                .thenComparing(x -> x.student().getName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static int roll(String r) {
        try {
            return r == null ? Integer.MAX_VALUE : Integer.parseInt(r.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private Map<Long, List<RebGuardian>> guardiansByStudent() {
        Map<Long, List<RebGuardian>> out = new HashMap<>();
        for (RebGuardian g : guardians.findAll()) out.computeIfAbsent(g.getStudentId(), k -> new ArrayList<>()).add(g);
        out.values().forEach(l -> l.sort(Comparator.comparing((RebGuardian g) -> !g.isPrimary()).thenComparing(RebGuardian::getId)));
        return out;
    }

    private List<Map<String, Object>> rows(String key, LocalDate from, LocalDate to) {
        return switch (key) {
            case "reb_strength" -> strength();
            case "reb_students" -> girls();
            case "reb_admissions_leavers" -> admissionsAndLeavers(from, to);
            case "reb_guardian_contacts" -> contacts();
            case "reb_board_candidates" -> boardCandidates();
            case "reb_staff" -> staffRows();
            default -> throw bad("Unknown report.");
        };
    }

    private List<Map<String, Object>> strength() {
        Map<String, Map<String, Object>> bySection = new LinkedHashMap<>();
        for (Seat x : seats()) {
            Map<String, Object> r = bySection.computeIfAbsent(x.label(), k -> {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("class", x.cls() == null ? null : x.cls().getName());
                n.put("section", x.section() == null ? null : x.section().getName());
                for (String c : List.of("girls", "residential", "day_scholars", "self_paying", "sponsored", "free")) n.put(c, 0);
                return n;
            });
            r.merge("girls", 1, (a, b) -> (Integer) a + (Integer) b);
            r.merge(x.student().isResidential() ? "residential" : "day_scholars", 1, (a, b) -> (Integer) a + (Integer) b);
            String plan = switch (x.student().getFeePlan()) { case "SPONSORED" -> "sponsored"; case "FREE" -> "free"; default -> "self_paying"; };
            r.merge(plan, 1, (a, b) -> (Integer) a + (Integer) b);
        }
        List<Map<String, Object>> rows = new ArrayList<>(bySection.values());
        if (!rows.isEmpty()) {
            Map<String, Object> total = new LinkedHashMap<>();
            total.put("class", "Total");
            total.put("section", "");
            for (String c : List.of("girls", "residential", "day_scholars", "self_paying", "sponsored", "free")) {
                total.put(c, rows.stream().mapToInt(r -> (Integer) r.get(c)).sum());
            }
            rows.add(total);
        }
        return rows;
    }

    private List<Map<String, Object>> girls() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Seat x : seats()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("admission_no", x.student().getAdmissionNo());
            r.put("name", x.student().getName());
            r.put("class", x.label());
            r.put("roll_no", x.enrollment().getRollNo());
            r.put("date_of_birth", x.student().getDateOfBirth());
            r.put("residential", x.student().isResidential() ? "Yes" : "No");
            r.put("fee_plan", plan(x.student().getFeePlan()));
            r.put("district", x.student().getDistrict());
            r.put("admitted_on", x.student().getAdmittedOn());
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> admissionsAndLeavers(LocalDate from, LocalDate to) {
        RebSession session = setup.currentSession();
        LocalDate start = from != null ? from : session.getStartDate();
        LocalDate end = to != null ? to : session.getEndDate();
        if (end.isBefore(start)) throw bad("The end date is before the start date.");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebStudent s : students.findAll()) {
            if (s.getAdmittedOn() != null && !s.getAdmittedOn().isBefore(start) && !s.getAdmittedOn().isAfter(end)) {
                rows.add(event(s, s.getAdmittedOn(), "Admitted", null));
            }
            if (s.getLeftOn() != null && !"ACTIVE".equals(s.getStatus()) && !s.getLeftOn().isBefore(start) && !s.getLeftOn().isAfter(end)) {
                rows.add(event(s, s.getLeftOn(), "PASSED_OUT".equals(s.getStatus()) ? "Passed out" : "Left", s.getLeavingReason()));
            }
        }
        rows.sort(Comparator.comparing(r -> (LocalDate) r.get("date")));
        return rows;
    }

    private static Map<String, Object> event(RebStudent s, LocalDate day, String what, String reason) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("date", day);
        r.put("event", what);
        r.put("admission_no", s.getAdmissionNo());
        r.put("name", s.getName());
        r.put("reason", reason);
        return r;
    }

    private List<Map<String, Object>> contacts() {
        Map<Long, List<RebGuardian>> gs = guardiansByStudent();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Seat x : seats()) {
            List<RebGuardian> list = gs.getOrDefault(x.student().getId(), List.of());
            RebGuardian a = list.isEmpty() ? null : list.get(0), b = list.size() < 2 ? null : list.get(1);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("class", x.label());
            r.put("roll_no", x.enrollment().getRollNo());
            r.put("name", x.student().getName());
            r.put("guardian", a == null ? null : a.getName() + " (" + relation(a.getRelation()) + ")");
            r.put("phone", a == null ? null : a.getPhone());
            r.put("whatsapp", a == null ? null : a.getWhatsapp());
            r.put("second_guardian", b == null ? null : b.getName() + " (" + relation(b.getRelation()) + ")");
            r.put("second_phone", b == null ? null : b.getPhone());
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> boardCandidates() {
        Map<Long, List<RebGuardian>> gs = guardiansByStudent();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Seat x : seats()) {
            if (x.cls() == null || !x.cls().isBoardExam()) continue;
            List<RebGuardian> list = gs.getOrDefault(x.student().getId(), List.of());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("class", x.label());
            r.put("roll_no", x.enrollment().getRollNo());
            r.put("admission_no", x.student().getAdmissionNo());
            r.put("name", x.student().getName());
            r.put("father", list.stream().filter(g -> "FATHER".equals(g.getRelation())).map(RebGuardian::getName).findFirst().orElse(null));
            r.put("mother", list.stream().filter(g -> "MOTHER".equals(g.getRelation())).map(RebGuardian::getName).findFirst().orElse(null));
            r.put("date_of_birth", x.student().getDateOfBirth());
            r.put("category", x.student().getSocialCategory());
            r.put("board_registration_no", x.student().getBoardRegistrationNo());
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> staffRows() {
        Long session = setup.sessionsCurrentId();
        Map<Long, String> labels = studentService.sectionLabels();
        Map<Long, String> subjectNames = new HashMap<>();
        subjects.findAll().forEach(s -> subjectNames.put(s.getId(), s.getName()));
        Map<Long, List<String>> teaches = new HashMap<>();
        if (session != null) {
            for (RebTeacherAssignment a : assignments.findBySessionId(session)) {
                teaches.computeIfAbsent(a.getStaffId(), k -> new ArrayList<>()).add(subjectNames.get(a.getSubjectId()) + " (" + labels.get(a.getSectionId()) + ")");
            }
        }
        Map<Long, List<String>> classTeacherOf = new HashMap<>();
        if (session != null) {
            for (RebSection s : sections.findBySessionId(session)) {
                if (s.getClassTeacherStaffId() != null) classTeacherOf.computeIfAbsent(s.getClassTeacherStaffId(), k -> new ArrayList<>()).add(labels.get(s.getId()));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebStaff s : staff.findAllByOrderByNameAsc()) {
            if (!"ACTIVE".equals(s.getStatus())) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", s.getName());
            r.put("designation", s.getDesignation().charAt(0) + s.getDesignation().substring(1).toLowerCase(Locale.ROOT));
            r.put("phone", s.getPhone());
            r.put("class_teacher_of", String.join(", ", classTeacherOf.getOrDefault(s.getId(), List.of())));
            List<String> list = teaches.getOrDefault(s.getId(), new ArrayList<>());
            Collections.sort(list);
            r.put("teaches", String.join("; ", list));
            rows.add(r);
        }
        return rows;
    }

    static String plan(String p) {
        return switch (p == null ? "" : p) { case "SPONSORED" -> "Sponsored"; case "FREE" -> "Free"; default -> "Self-paying"; };
    }

    static String relation(String r) {
        return r == null ? "" : r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT);
    }
}
