package com.rahbar.service;

import com.rahbar.entity.RebClass;
import com.rahbar.repository.RebClassRepository;
import com.rahbar.entity.RebGuardian;
import com.rahbar.repository.RebGuardianRepository;
import com.rahbar.entity.RebSection;
import com.rahbar.repository.RebSectionRepository;
import com.rahbar.entity.RebSession;
import com.rahbar.entity.RebStudent;
import com.rahbar.repository.RebStudentRepository;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * Brings the girls already at REB in from a spreadsheet (saved as CSV) into the current session. Each row is saved
 * on its own, so one bad row doesn't stop the rest. A girl whose admission number is already known is updated
 * instead of added again, so the same file can be imported twice.
 */
@Service
public class RebImportService {

    public static final String TEMPLATE = "Admission No,Name,Date of Birth,Class,Section,Roll No,Residential,Fee Plan,Admitted On,"
            + "Guardian Name,Relation,Guardian Phone,Address,District,State,Board Registration No,Previous School\n"
            + ",Ayesha Parveen,2014-03-12,Class 6,A,1,Yes,Sponsored,2024-04-05,Mohammad Imran,Father,9876543210,"
            + "\"Village Rampur, PO Sitamarhi\",Sitamarhi,Bihar,,Govt. Middle School Rampur\n";

    private final RebStudentService studentService;
    private final RebStudentRepository students;
    private final RebGuardianRepository guardians;
    private final RebClassRepository classes;
    private final RebSectionRepository sections;
    private final RebSetupService setup;
    private final TransactionTemplate tx;

    public RebImportService(RebStudentService studentService, RebStudentRepository students, RebGuardianRepository guardians,
                            RebClassRepository classes, RebSectionRepository sections, RebSetupService setup,
                            PlatformTransactionManager transactionManager) {
        this.studentService = studentService;
        this.students = students;
        this.guardians = guardians;
        this.classes = classes;
        this.sections = sections;
        this.setup = setup;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** { added, updated, skipped, problems: [{ row, name, reason }] }. */
    public Map<String, Object> importCsv(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw bad("Choose a CSV file.");
        RebSession session = setup.currentSession();
        Map<String, RebClass> classByName = new HashMap<>();
        classes.findAll().forEach(c -> classByName.put(c.getName().strip().toLowerCase(Locale.ROOT), c));
        int added = 0, updated = 0, skipped = 0;
        List<Map<String, Object>> problems = new ArrayList<>();

        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true)
                .setTrim(true).setAllowMissingColumnNames(true).setIgnoreEmptyLines(true).build()
                .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> headers = parser.getHeaderNames().stream().map(RebImportService::key).toList();
            if (!headers.contains("name") || !headers.contains("class")) throw bad("The file needs at least the columns Name and Class (see the template).");
            for (CSVRecord rec : parser) {
                int rowNo = (int) rec.getRecordNumber() + 1;
                Map<String, String> r = new HashMap<>();
                for (int i = 0; i < parser.getHeaderNames().size() && i < rec.size(); i++) r.put(key(parser.getHeaderNames().get(i)), rec.get(i).strip());
                String name = r.getOrDefault("name", "");
                if (name.isEmpty()) { skipped++; continue; }
                try {
                    boolean isNew = tx.execute(status -> importRow(r, session, classByName));
                    if (isNew) added++; else updated++;
                } catch (RuntimeException e) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("row", rowNo);
                    p.put("name", name);
                    p.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    problems.add(p);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        out.put("updated", updated);
        out.put("skipped", skipped);
        out.put("problems", problems);
        out.put("message", added + " added, " + updated + " updated" + (problems.isEmpty() ? "." : ", " + problems.size() + " rows with problems."));
        return out;
    }

    /** "Date of Birth" -> "dateofbirth". */
    static String key(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** True when a new girl was added. */
    private boolean importRow(Map<String, String> r, RebSession session, Map<String, RebClass> classByName) {
        RebClass cls = classByName.get(r.getOrDefault("class", "").strip().toLowerCase(Locale.ROOT));
        if (cls == null) throw bad("Unknown class \"" + r.get("class") + "\". Add it in REB Setup > Classes first.");
        String sectionName = r.getOrDefault("section", "").isBlank() ? "A" : r.get("section").strip().toUpperCase(Locale.ROOT);
        RebSection section = sections.findBySessionIdAndClassId(session.getId(), cls.getId()).stream()
                .filter(s -> s.getName().equalsIgnoreCase(sectionName)).findFirst()
                .orElseGet(() -> { // the section doesn't exist in this session yet: add it
                    RebSection s = new RebSection();
                    s.setSessionId(session.getId());
                    s.setClassId(cls.getId());
                    s.setName(sectionName);
                    return sections.save(s);
                });

        Map<String, Object> body = new HashMap<>();
        body.put("name", r.get("name"));
        body.put("dateOfBirth", r.get("dateofbirth"));
        body.put("residential", r.getOrDefault("residential", "yes"));
        body.put("feePlan", plan(r.get("feeplan")));
        body.put("admittedOn", r.get("admittedon"));
        body.put("address", r.get("address"));
        body.put("district", r.get("district"));
        body.put("state", r.get("state"));
        body.put("boardRegistrationNo", r.get("boardregistrationno"));
        body.put("previousSchool", r.get("previousschool"));
        String rollNo = text(r.get("rollno"), "Roll number", 10, false);

        String no = text(r.get("admissionno"), "Admission number", 30, false);
        RebStudent existing = no == null ? null : students.findByAdmissionNo(no).orElse(null);
        if (existing != null) {
            body.entrySet().removeIf(e -> e.getValue() == null || String.valueOf(e.getValue()).isBlank());
            studentService.readStudent(existing, body, false);
            students.save(existing);
            studentService.enroll(existing.getId(), section, rollNo);
            String phone = text(r.get("guardianphone"), "Guardian phone", 20, false);
            String gName = text(r.get("guardianname"), "Guardian name", 120, false);
            if (gName != null && guardians.findByStudentIdOrderByPrimaryDescIdAsc(existing.getId()).isEmpty()) {
                addGuardian(existing.getId(), gName, r.get("relation"), phone);
            }
            return false;
        }

        String gName = text(r.get("guardianname"), "Guardian name", 120, false);
        if (gName == null) throw bad("No Guardian Name.");
        RebStudent s = new RebStudent();
        studentService.readStudent(s, body, true);
        if (s.getAdmittedOn() == null) s.setAdmittedOn(session.getStartDate());
        s.setAdmissionNo(no != null ? no : studentService.nextAdmissionNo(s.getAdmittedOn()));
        students.save(s);
        addGuardian(s.getId(), gName, r.get("relation"), text(r.get("guardianphone"), "Guardian phone", 20, false));
        studentService.enroll(s.getId(), section, rollNo);
        return true;
    }

    private void addGuardian(Long studentId, String name, String relation, String phone) {
        RebGuardian g = new RebGuardian();
        g.setStudentId(studentId);
        g.setName(name);
        g.setRelation(code(relation, RebStudentService.RELATIONS, "FATHER", "relation"));
        g.setPhone(phone);
        g.setPrimary(true);
        guardians.save(g);
    }

    /** "Sponsored" / "self" / "Free" / "self-pay" -> SPONSORED / SELF / FREE. */
    private static String plan(String v) {
        String s = v == null ? "" : v.strip().toUpperCase(Locale.ROOT);
        if (s.startsWith("SPONSOR")) return "SPONSORED";
        if (s.startsWith("FREE") || s.startsWith("WAIV")) return "FREE";
        return "SELF";
    }
}
