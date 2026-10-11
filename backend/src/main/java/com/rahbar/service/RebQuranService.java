package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.security.RebClassScope;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * Quran / Hifz progress of REB girls: one entry per lesson (sabaq = new lesson, sabqi = recent revision, manzil =
 * older revision) with the para, surah, ayahs and how well she knew it. A section's summary shows each girl's
 * current para and how many paras she has completed. Teachers with the "own REB classes" scope see their classes.
 */
@Service
public class RebQuranService {

    static final Set<String> KINDS = Set.of("SABAQ", "SABQI", "MANZIL");
    static final Set<String> QUALITIES = Set.of("EXCELLENT", "GOOD", "NEEDS_REVISION");

    private final RebQuranProgressRepository progress;
    private final RebStudentRepository students;
    private final RebEnrollmentRepository enrollments;
    private final RebStaffRepository staff;
    private final RebSessionRepository sessions;
    private final RebClassScope classScope;

    public RebQuranService(RebQuranProgressRepository progress, RebStudentRepository students, RebEnrollmentRepository enrollments,
                           RebStaffRepository staff, RebSessionRepository sessions, RebClassScope classScope) {
        this.progress = progress;
        this.students = students;
        this.enrollments = enrollments;
        this.staff = staff;
        this.sessions = sessions;
        this.classScope = classScope;
    }

    /** 404 unless the user may see the girl (a teacher: girls of her classes in any session). */
    private RebStudent requireGirl(Long studentId) {
        RebStudent g = students.findById(studentId).orElseThrow(() -> notFound("Student"));
        RebClassScope.Scope scope = classScope.current();
        if (!scope.all() && enrollments.findByStudentId(studentId).stream().noneMatch(e -> scope.seesSection(e.getSectionId()))) {
            throw notFound("Student");
        }
        return g;
    }

    /** A girl's lessons, newest first, with her summary. */
    public Map<String, Object> ofStudent(Long studentId) {
        RebStudent g = requireGirl(studentId);
        List<RebQuranProgress> list = progress.findByStudentIdOrderByDateDescIdDesc(studentId);
        Map<Long, String> teachers = new HashMap<>();
        staff.findAll().forEach(s -> teachers.put(s.getId(), s.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebQuranProgress p : list) {
            Map<String, Object> r = Rows.of(p);
            r.put("teacher_name", teachers.get(p.getTeacherStaffId()));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>(summary(list));
        out.put("student", Rows.pick(g, "id", "admission_no", "name"));
        out.put("entries", rows);
        return out;
    }

    /** Current para (of the latest new lesson), paras completed, the last lesson day. */
    static Map<String, Object> summary(List<RebQuranProgress> list) {
        Set<Integer> completed = new TreeSet<>();
        Integer current = null;
        LocalDate last = null;
        for (RebQuranProgress p : list) { // newest first
            if (p.isCompletedPara()) completed.add(p.getPara());
            if (current == null && "SABAQ".equals(p.getKind())) current = p.getPara();
            if (last == null || p.getDate().isAfter(last)) last = p.getDate();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current_para", current);
        out.put("paras_completed", completed.size());
        out.put("completed", new ArrayList<>(completed));
        out.put("last_lesson", last);
        return out;
    }

    /** Body: { studentId, date (default today), kind, para, surah, fromAyah, toAyah, quality, completedPara, remark }. */
    @Transactional
    public RebQuranProgress add(Map<String, Object> body) {
        RebStudent g = requireGirl(id(body.get("studentId")));
        RebQuranProgress p = new RebQuranProgress();
        p.setStudentId(g.getId());
        LocalDate day = date(body.get("date"), "Date");
        if (day == null) day = LocalDate.now();
        if (day.isAfter(LocalDate.now())) throw bad("The day is in the future.");
        p.setDate(day);
        p.setKind(code(body.get("kind"), KINDS, "SABAQ", "lesson"));
        Integer para = number(body.get("para"), "Para", 1, 30);
        if (para == null) throw bad("Enter the para (1 to 30).");
        p.setPara(para);
        p.setSurah(text(body.get("surah"), "Surah", 60, false));
        p.setFromAyah(number(body.get("fromAyah"), "From ayah", 1, 286));
        p.setToAyah(number(body.get("toAyah"), "To ayah", 1, 286));
        if (p.getFromAyah() != null && p.getToAyah() != null && p.getToAyah() < p.getFromAyah()) throw bad("The last ayah is before the first.");
        p.setQuality(code(body.get("quality"), QUALITIES, "GOOD", "how well she knew it"));
        p.setCompletedPara(flag(body.get("completedPara"), false));
        p.setRemark(text(body.get("remark"), "Remark", 300, false));
        p.setTeacherStaffId(staff.findByUserId(Access.current().getUser().getId()).map(RebStaff::getId).orElse(null));
        return progress.save(p);
    }

    @Transactional
    public void delete(Long id) {
        RebQuranProgress p = progress.findById(id).orElseThrow(() -> notFound("Entry"));
        requireGirl(p.getStudentId());
        progress.delete(p);
    }

    /** A section's girls with their current para, paras completed and last lesson. */
    public List<Map<String, Object>> sectionSummary(Long sectionId) {
        classScope.requireSection(sectionId);
        List<RebEnrollment> roll = enrollments.findBySectionId(sectionId).stream().filter(e -> "ACTIVE".equals(e.getStatus())).toList();
        Map<Long, List<RebQuranProgress>> byGirl = new HashMap<>();
        progress.findByStudentIdIn(roll.stream().map(RebEnrollment::getStudentId).toList())
                .forEach(p -> byGirl.computeIfAbsent(p.getStudentId(), k -> new ArrayList<>()).add(p));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebEnrollment e : roll) {
            RebStudent g = students.findById(e.getStudentId()).orElse(null);
            if (g == null) continue;
            List<RebQuranProgress> list = new ArrayList<>(byGirl.getOrDefault(g.getId(), List.of()));
            list.sort(Comparator.comparing(RebQuranProgress::getDate).thenComparing(RebQuranProgress::getId).reversed());
            Map<String, Object> r = new LinkedHashMap<>(summary(list));
            r.put("student_id", g.getId());
            r.put("name", g.getName());
            r.put("roll_no", e.getRollNo());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> RebAttendanceService.rollNumber((String) r.get("roll_no"))).thenComparing(r -> String.valueOf(r.get("name"))));
        return rows;
    }
}
