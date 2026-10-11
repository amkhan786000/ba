package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB fee set-up: fee heads, the amount of every head for every class in a session (one grid), and concessions
 * for individual girls. Bills are made from these by RebBillingService.
 */
@Service
public class RebFeeService {

    static final Set<String> FREQUENCIES = Set.of("MONTHLY", "ANNUAL", "ONE_TIME");
    static final Set<String> CONCESSION_KINDS = Set.of("PERCENT", "AMOUNT");
    static final Set<String> CONCESSION_REASONS = Set.of("ORPHAN", "SIBLING", "STAFF_CHILD", "NEED", "OTHER");

    private final RebFeeHeadRepository heads;
    private final RebFeeStructureRepository structures;
    private final RebConcessionRepository concessions;
    private final RebClassRepository classes;
    private final RebStudentRepository students;
    private final RebSetupService setup;

    public RebFeeService(RebFeeHeadRepository heads, RebFeeStructureRepository structures, RebConcessionRepository concessions,
                         RebClassRepository classes, RebStudentRepository students, RebSetupService setup) {
        this.heads = heads;
        this.structures = structures;
        this.concessions = concessions;
        this.classes = classes;
        this.students = students;
        this.setup = setup;
    }

    // ---------------------------------------------------------------- fee heads

    public List<Map<String, Object>> heads() {
        return Rows.list(heads.findAllByOrderByDisplayOrderAscNameAsc());
    }

    /** Body: { name, frequency, residentialOnly, displayOrder, active }. Heads are hidden, never deleted (bills use them). */
    @Transactional
    public RebFeeHead saveHead(Long id, Map<String, Object> body) {
        RebFeeHead h = id == null ? new RebFeeHead() : heads.findById(id).orElseThrow(() -> notFound("Fee head"));
        String name = text(body.get("name"), "Name", 80, true);
        if (id == null ? heads.existsByNameIgnoreCase(name) : heads.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw bad("There is already a fee head called " + name + ".");
        }
        h.setName(name);
        h.setFrequency(code(body.get("frequency"), FREQUENCIES, "MONTHLY", "frequency"));
        h.setResidentialOnly(flag(body.get("residentialOnly"), false));
        Integer order = number(body.get("displayOrder"), "Order", 0, 1000);
        h.setDisplayOrder(order == null ? 0 : order);
        h.setActive(flag(body.get("active"), true));
        return heads.save(h);
    }

    // ---------------------------------------------------------------- fee structure

    /**
     * The grid for a session: every active class (rows) by every active head (columns), with the amount and, for
     * annual heads, the month they are billed in.
     */
    public Map<String, Object> structure(Long sessionId) {
        RebSession session = setup.session(sessionId);
        Map<String, RebFeeStructure> byKey = new HashMap<>();
        structures.findBySessionId(session.getId()).forEach(s -> byKey.put(s.getClassId() + ":" + s.getFeeHeadId(), s));
        List<RebFeeHead> headList = heads.findAllByOrderByDisplayOrderAscNameAsc().stream().filter(RebFeeHead::isActive).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebClass c : classes.findAllByOrderByLevelOrderAscNameAsc()) {
            if (!c.isActive()) continue;
            List<Map<String, Object>> cells = new ArrayList<>();
            for (RebFeeHead h : headList) {
                RebFeeStructure s = byKey.get(c.getId() + ":" + h.getId());
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("fee_head_id", h.getId());
                cell.put("amount", s == null ? null : s.getAmount());
                cell.put("bill_month", s == null ? null : s.getBillMonth());
                cells.add(cell);
            }
            rows.add(Map.of("class_id", c.getId(), "class_name", c.getName(), "cells", cells));
        }
        return Map.of("session", Rows.of(session), "heads", Rows.list(headList), "rows", rows);
    }

    /**
     * Body: { sessionId, cells: [{ classId, feeHeadId, amount (empty = not charged), billMonth }] }. Changing an
     * amount affects bills made after the change, not bills already made.
     */
    @Transactional
    public void saveStructure(Map<String, Object> body) {
        RebSession session = setup.session(id(body.get("sessionId")));
        if (!(body.get("cells") instanceof List<?> list)) throw bad("Send the amounts.");
        Map<String, RebFeeStructure> existing = new HashMap<>();
        structures.findBySessionId(session.getId()).forEach(s -> existing.put(s.getClassId() + ":" + s.getFeeHeadId(), s));
        Map<Long, RebFeeHead> headById = new HashMap<>();
        heads.findAll().forEach(h -> headById.put(h.getId(), h));
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw bad("Invalid amount.");
            Long classId = id(m.get("classId")), headId = id(m.get("feeHeadId"));
            RebFeeHead head = headById.get(headId);
            if (classId == null || !classes.existsById(classId) || head == null) throw bad("Unknown class or fee head.");
            BigDecimal amount = money(m.get("amount"), "Amount");
            RebFeeStructure s = existing.get(classId + ":" + headId);
            if (amount == null || amount.signum() == 0) {
                if (s != null) structures.delete(s);
                continue;
            }
            Integer month = number(m.get("billMonth"), "Bill month", 1, 12);
            if ("ANNUAL".equals(head.getFrequency()) && month == null) throw bad(head.getName() + " is charged once a year: choose the month it is billed in.");
            if (s == null) {
                s = new RebFeeStructure();
                s.setSessionId(session.getId());
                s.setClassId(classId);
                s.setFeeHeadId(headId);
            }
            s.setAmount(amount);
            s.setBillMonth("ANNUAL".equals(head.getFrequency()) ? month : null);
            structures.save(s);
        }
    }

    // ---------------------------------------------------------------- concessions

    /** Concessions of a girl (all sessions), newest first. */
    public List<Map<String, Object>> concessions(Long studentId) {
        Map<Long, String> headNames = new HashMap<>();
        heads.findAll().forEach(h -> headNames.put(h.getId(), h.getName()));
        Map<Long, String> sessionNames = new HashMap<>();
        setup.sessions().forEach(s -> sessionNames.put((Long) s.get("id"), String.valueOf(s.get("name"))));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebConcession c : concessions.findByStudentIdOrderByIdDesc(studentId)) {
            Map<String, Object> r = Rows.of(c);
            r.put("fee_head_name", c.getFeeHeadId() == null ? "All fees" : headNames.get(c.getFeeHeadId()));
            r.put("session_name", sessionNames.get(c.getSessionId()));
            rows.add(r);
        }
        return rows;
    }

    /** Body: { studentId, sessionId, feeHeadId (empty = all), kind PERCENT | AMOUNT, value, reason, note, fromPeriod, toPeriod }. */
    @Transactional
    public RebConcession saveConcession(Long id, Map<String, Object> body) {
        RebConcession c = id == null ? new RebConcession() : concessions.findById(id).orElseThrow(() -> notFound("Concession"));
        if (id == null) {
            Long studentId = id(body.get("studentId"));
            if (studentId == null || !students.existsById(studentId)) throw bad("Unknown girl.");
            c.setStudentId(studentId);
        }
        c.setSessionId(setup.session(id(body.get("sessionId"))).getId());
        Long head = id(body.get("feeHeadId"));
        if (head != null && !heads.existsById(head)) throw bad("Unknown fee head.");
        c.setFeeHeadId(head);
        c.setKind(code(body.get("kind"), CONCESSION_KINDS, "PERCENT", "kind"));
        BigDecimal value = money(body.get("value"), "Concession");
        if (value == null || value.signum() == 0) throw bad("Enter the concession.");
        if ("PERCENT".equals(c.getKind()) && value.compareTo(BigDecimal.valueOf(100)) > 0) throw bad("A percentage can't be more than 100.");
        c.setValue(value);
        c.setReason(code(body.get("reason"), CONCESSION_REASONS, "NEED", "reason"));
        c.setNote(text(body.get("note"), "Note", 500, false));
        c.setFromPeriod(period(body.get("fromPeriod"), "From month"));
        c.setToPeriod(period(body.get("toPeriod"), "To month"));
        if (c.getFromPeriod() != null && c.getToPeriod() != null && c.getToPeriod().compareTo(c.getFromPeriod()) < 0) {
            throw bad("The last month is before the first month.");
        }
        return concessions.save(c);
    }

    @Transactional
    public void deleteConcession(Long id) {
        concessions.delete(concessions.findById(id).orElseThrow(() -> notFound("Concession")));
    }

    /** "2026-07" (also accepts a date like 2026-07-15); null when blank. */
    public static String period(Object v, String label) {
        String s = v == null ? "" : String.valueOf(v).strip();
        if (s.isEmpty()) return null;
        if (s.length() >= 7) s = s.substring(0, 7);
        if (!s.matches("\\d{4}-(0[1-9]|1[0-2])")) throw bad(label + " must be a month like 2026-07.");
        return s;
    }
}
