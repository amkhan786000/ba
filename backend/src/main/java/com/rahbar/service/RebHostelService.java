package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * Hostel leave of residential REB girls: a gate pass is given only to a guardian allowed to take her home
 * (REB > Girls > guardians, "may take her home"), with the day she is due back; the office marks her returned.
 * Girls still out after that day are overdue.
 */
@Service
public class RebHostelService {

    private final RebHostelLeaveRepository leaves;
    private final RebStudentRepository students;
    private final RebGuardianRepository guardians;
    private final RebStudentService studentService;

    public RebHostelService(RebHostelLeaveRepository leaves, RebStudentRepository students, RebGuardianRepository guardians,
                            RebStudentService studentService) {
        this.leaves = leaves;
        this.students = students;
        this.guardians = guardians;
        this.studentService = studentService;
    }

    /** Leaves (status OUT, OVERDUE, RETURNED or all), newest first, with the girl and the guardian. */
    public List<Map<String, Object>> list(String status) {
        Map<Long, RebStudent> girls = new HashMap<>();
        students.findAll().forEach(s -> girls.put(s.getId(), s));
        Map<Long, RebGuardian> gs = new HashMap<>();
        guardians.findAll().forEach(g -> gs.put(g.getId(), g));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebHostelLeave l : leaves.findAllByOrderByLeaveFromDesc()) {
            boolean overdue = overdue(l);
            if ("OVERDUE".equalsIgnoreCase(status) ? !overdue : status != null && !status.isBlank() && !status.equalsIgnoreCase(l.getStatus())) continue;
            rows.add(row(l, girls.get(l.getStudentId()), gs.get(l.getGuardianId())));
        }
        return rows;
    }

    static boolean overdue(RebHostelLeave l) {
        return "OUT".equals(l.getStatus()) && l.getLeaveTo().isBefore(LocalDate.now());
    }

    private static Map<String, Object> row(RebHostelLeave l, RebStudent g, RebGuardian guardian) {
        Map<String, Object> r = Rows.of(l);
        r.put("student_name", g == null ? null : g.getName());
        r.put("admission_no", g == null ? null : g.getAdmissionNo());
        r.put("guardian_name", guardian == null ? null : guardian.getName());
        r.put("guardian_relation", guardian == null ? null : guardian.getRelation());
        r.put("guardian_phone", guardian == null ? null : guardian.getPhone());
        r.put("overdue", overdue(l));
        return r;
    }

    public List<Map<String, Object>> ofStudent(Long studentId) {
        RebStudent g = studentService.require(studentId);
        Map<Long, RebGuardian> gs = new HashMap<>();
        guardians.findByStudentIdOrderByPrimaryDescIdAsc(studentId).forEach(x -> gs.put(x.getId(), x));
        return leaves.findByStudentIdOrderByLeaveFromDesc(studentId).stream().map(l -> row(l, g, gs.get(l.getGuardianId()))).toList();
    }

    /** Body: { studentId, guardianId, leaveTo, reason, note }. Only to a guardian who may take her home. */
    @Transactional
    public RebHostelLeave give(Map<String, Object> body) {
        RebStudent g = studentService.require(id(body.get("studentId")));
        if (!"ACTIVE".equals(g.getStatus())) throw bad("She is not studying at REB now.");
        if (!g.isResidential()) throw bad("She doesn't live in the hostel.");
        if (leaves.findByStudentIdOrderByLeaveFromDesc(g.getId()).stream().anyMatch(l -> "OUT".equals(l.getStatus()))) {
            throw bad("She is already out on leave. Mark her returned first.");
        }
        RebGuardian guardian = guardians.findById(id(body.get("guardianId"))).orElse(null);
        if (guardian == null || !guardian.getStudentId().equals(g.getId())) throw bad("Choose the guardian who is taking her.");
        if (!guardian.isMayPickUp()) throw bad(guardian.getName() + " is not allowed to take her home (see her guardians).");
        LocalDate back = date(body.get("leaveTo"), "Back on");
        if (back == null) throw bad("Enter the day she is due back.");
        if (back.isBefore(LocalDate.now())) throw bad("The return day is in the past.");
        RebHostelLeave l = new RebHostelLeave();
        String prefix = "GP/" + LocalDate.now().getYear() + "/";
        l.setPassNo(prefix + String.format("%04d", RebBillingService.nextNumber(leaves.findNumbersStartingWith(prefix), prefix)));
        l.setStudentId(g.getId());
        l.setGuardianId(guardian.getId());
        l.setLeaveFrom(LocalDateTime.now());
        l.setLeaveTo(back);
        l.setReason(text(body.get("reason"), "Reason", 300, true));
        l.setNote(text(body.get("note"), "Note", 500, false));
        return leaves.save(l);
    }

    /** Body: { returnedWith, note }. */
    @Transactional
    public RebHostelLeave markReturned(Long id, Map<String, Object> body) {
        RebHostelLeave l = leaves.findById(id).orElseThrow(() -> notFound("Leave"));
        if (!"OUT".equals(l.getStatus())) throw bad("She is already back.");
        l.setStatus("RETURNED");
        l.setReturnedAt(LocalDateTime.now());
        l.setReturnedWith(text(body.get("returnedWith"), "Brought back by", 120, false));
        String note = text(body.get("note"), "Note", 500, false);
        if (note != null) l.setNote(l.getNote() == null ? note : l.getNote() + " / " + note);
        return leaves.save(l);
    }
}
