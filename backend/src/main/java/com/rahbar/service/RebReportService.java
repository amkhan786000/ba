package com.rahbar.service;

import com.rahbar.dto.RebReportDefinition;
import com.rahbar.entity.RebClass;
import com.rahbar.repository.RebClassRepository;
import com.rahbar.entity.RebEnrollment;
import com.rahbar.repository.RebEnrollmentRepository;
import com.rahbar.entity.RebGuardian;
import com.rahbar.repository.RebGuardianRepository;
import com.rahbar.entity.RebSection;
import com.rahbar.repository.RebSectionRepository;
import com.rahbar.entity.RebSession;
import com.rahbar.entity.RebStaff;
import com.rahbar.repository.RebStaffRepository;
import com.rahbar.entity.RebStudent;
import com.rahbar.repository.RebStudentRepository;
import com.rahbar.repository.RebSubjectRepository;
import com.rahbar.entity.RebTeacherAssignment;
import com.rahbar.repository.RebTeacherAssignmentRepository;

import com.rahbar.service.ReportService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB reports (Rahbar-e-Banaat > Reports), viewed on screen or downloaded as CSV / Excel / PDF through Rahbar's
 * ReportService. They describe the current session unless a date range is given.
 */
@Service
public class RebReportService {

    static final List<RebReportDefinition> CATALOG = List.of(
            new RebReportDefinition("reb_strength", "Students", "Strength by class",
                    "Girls in each section of the current session: residential, day scholars and fee plans.", false),
            new RebReportDefinition("reb_students", "Students", "List of girls",
                    "Every girl studying now with class, roll number, date of birth and fee plan.", false),
            new RebReportDefinition("reb_admissions_leavers", "Students", "Admissions and leavers",
                    "Girls admitted, and girls who left or passed out, in the date range (default: the current session).", true),
            new RebReportDefinition("reb_guardian_contacts", "Students", "Guardian contacts",
                    "Class lists with the parents' and guardians' names and phone numbers.", false),
            new RebReportDefinition("reb_board_candidates", "Students", "Board exam candidates",
                    "Girls in the Bihar Board exam class(es) with their board registration details.", false),
            new RebReportDefinition("reb_staff", "Staff", "Teachers and subjects",
                    "Every staff member with the sections and subjects they teach in the current session.", false),
            new RebReportDefinition("reb_fee_collection", "Fees", "Fee collection",
                    "Every payment in the date range (default: this month): receipt, girl, family or sponsor, mode and amount.", true),
            new RebReportDefinition("reb_outstanding", "Fees", "Outstanding fees",
                    "Girls with unpaid bills: family and sponsor shares, number of bills and the oldest unpaid month.", false),
            new RebReportDefinition("reb_concessions", "Fees", "Concessions",
                    "Fee concessions of the current session: who, how much, why and who approved them.", false),
            new RebReportDefinition("reb_sponsorship_coverage", "Fees", "Sponsorship coverage",
                    "Every girl studying now with her fee plan and sponsor (name and ID only), and girls without a sponsor.", false));

    /** Reports with money in them: only for people who may see REB fees. */
    static final Set<String> FEE_REPORTS = Set.of("reb_fee_collection", "reb_outstanding", "reb_concessions", "reb_sponsorship_coverage");

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

    public List<RebReportDefinition> catalog() {
        boolean fees = com.rahbar.security.Access.can(com.rahbar.security.Section.REB_FEES, com.rahbar.security.Section.Level.VIEW);
        return CATALOG.stream().filter(d -> fees || !FEE_REPORTS.contains(d.key())).toList();
    }

    private static RebReportDefinition definition(String key) {
        RebReportDefinition d = CATALOG.stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow(() -> bad("Unknown report."));
        if (FEE_REPORTS.contains(key) && !com.rahbar.security.Access.can(com.rahbar.security.Section.REB_FEES, com.rahbar.security.Section.Level.VIEW)) {
            throw com.rahbar.security.Access.forbidden("This report needs the \"REB: fees\" permission.");
        }
        return d;
    }

    public Map<String, Object> data(String key, LocalDate from, LocalDate to) {
        RebReportDefinition def = definition(key);
        List<Map<String, Object>> rows = rows(key, from, to);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("title", def.title());
        body.put("columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()));
        body.put("rows", rows);
        return body;
    }

    public ReportService.Report build(String key, String format, LocalDate from, LocalDate to) {
        RebReportDefinition def = definition(key);
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
            case "reb_fee_collection" -> feeCollection(from, to);
            case "reb_outstanding" -> outstanding();
            case "reb_concessions" -> concessionRows();
            case "reb_sponsorship_coverage" -> sponsorshipCoverage();
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

    @org.springframework.beans.factory.annotation.Autowired
    private RebBillingService billing;
    @org.springframework.beans.factory.annotation.Autowired
    private RebSponsorshipService sponsorships;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.RebConcessionRepository concessions;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.RebFeeHeadRepository feeHeads;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.UserRepository users;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> feeCollection(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : java.time.YearMonth.now().atDay(1);
        LocalDate end = to != null ? to : LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> p : (List<Map<String, Object>>) billing.payments(start, end).get("payments")) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", p.get("paid_on"));
            r.put("receipt_no", p.get("receipt_no"));
            r.put("admission_no", p.get("admission_no"));
            r.put("name", p.get("student_name"));
            r.put("paid_by", "SPONSOR".equals(p.get("payer")) ? "Sponsor: " + p.get("sponsor_name") : "Family");
            r.put("mode", p.get("method"));
            r.put("reference", p.get("reference"));
            r.put("amount", "VALID".equals(p.get("status")) ? p.get("amount") : java.math.BigDecimal.ZERO);
            r.put("status", "VALID".equals(p.get("status")) ? "" : "Cancelled: " + p.get("cancel_reason"));
            r.put("received_by", p.get("received_by"));
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> outstanding() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> d : billing.dues(null, true)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("admission_no", d.get("admission_no"));
            r.put("name", d.get("name"));
            r.put("class", d.get("section"));
            r.put("unpaid_bills", d.get("bills"));
            r.put("oldest_unpaid", d.get("oldest_label"));
            r.put("family_owes", d.get("family_outstanding"));
            r.put("sponsor_owes", d.get("sponsor_outstanding"));
            r.put("total", d.get("total"));
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> concessionRows() {
        RebSession session = setup.currentSession();
        Map<Long, RebStudent> girls = new HashMap<>();
        students.findAll().forEach(s -> girls.put(s.getId(), s));
        Map<Long, String> headNames = new HashMap<>();
        feeHeads.findAll().forEach(h -> headNames.put(h.getId(), h.getName()));
        List<com.rahbar.entity.RebConcession> list = concessions.findBySessionId(session.getId());
        Map<Long, String> who = new HashMap<>();
        users.findAllById(list.stream().map(com.rahbar.entity.RebConcession::getCreatedBy).filter(Objects::nonNull).distinct().toList())
                .forEach(u -> who.put(u.getId(), u.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.rahbar.entity.RebConcession c : list) {
            RebStudent g = girls.get(c.getStudentId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("admission_no", g == null ? null : g.getAdmissionNo());
            r.put("name", g == null ? null : g.getName());
            r.put("on", c.getFeeHeadId() == null ? "All fees" : headNames.get(c.getFeeHeadId()));
            r.put("concession", "PERCENT".equals(c.getKind()) ? c.getValue().stripTrailingZeros().toPlainString() + "%" : "Rs. " + c.getValue().toPlainString());
            r.put("reason", relation(c.getReason()).replace('_', ' '));
            r.put("months", (c.getFromPeriod() == null ? "start" : c.getFromPeriod()) + " to " + (c.getToPeriod() == null ? "end" : c.getToPeriod()));
            r.put("note", c.getNote());
            r.put("approved_by", who.get(c.getCreatedBy()));
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> sponsorshipCoverage() {
        String now = java.time.YearMonth.now().toString();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Seat x : seats()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("class", x.label());
            r.put("admission_no", x.student().getAdmissionNo());
            r.put("name", x.student().getName());
            r.put("fee_plan", plan(x.student().getFeePlan()));
            var sp = sponsorships.covering(x.student().getId(), now);
            var sponsor = sp.flatMap(s -> users.findById(s.getSponsorUserId()));
            r.put("sponsor", sponsor.map(u -> u.getName() + (u.getUserId() == null ? "" : " (" + u.getUserId() + ")")).orElse(""));
            r.put("per_month", sp.map(com.rahbar.entity.RebSponsorship::getMonthlyAmount).orElse(null));
            r.put("since", sp.map(com.rahbar.entity.RebSponsorship::getStartPeriod).orElse(null));
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
