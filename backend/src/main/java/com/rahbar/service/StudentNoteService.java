package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Role;
import com.rahbar.entity.StudentNote;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.StudentNoteRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.security.StudentScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Notes to sponsors: a student writes a short update or thank-you note to their sponsor; the office approves it
 * (Sponsors & Payments > Notes to sponsors) or returns it with a reason; the sponsor sees approved notes on their dashboard and
 * gets them by email.
 */
@Service
public class StudentNoteService {

    public static final Map<String, String> KINDS = new LinkedHashMap<>();
    static {
        KINDS.put("UPDATE", "Update");
        KINDS.put("THANK_YOU", "Thank-you note");
    }
    static final int MAX_LENGTH = 1000;
    /** A student can't have more than this many notes waiting for the office. */
    static final int MAX_PENDING = 3;

    private final StudentNoteRepository notes;
    private final UserRepository users;
    private final GrantorGranteeRepository mappings;
    private final RoleRepository roles;
    private final NotificationService notifications;
    private final StudentScope studentScope;

    public StudentNoteService(StudentNoteRepository notes, UserRepository users, GrantorGranteeRepository mappings,
                              RoleRepository roles, NotificationService notifications, StudentScope studentScope) {
        this.notes = notes;
        this.users = users;
        this.mappings = mappings;
        this.roles = roles;
        this.notifications = notifications;
        this.studentScope = studentScope;
    }

    // ---------------------------------------------------------------- student

    /** The student's notes (newest first) and whether they have a sponsor to write to. */
    @Transactional(readOnly = true)
    public Map<String, Object> mine(Long studentId) {
        Optional<GrantorGrantee> sponsor = currentSponsor(studentId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sponsor_name", sponsor.flatMap(g -> users.findById(g.getGrantorId())).map(User::getName).orElse(null));
        body.put("notes", notes.findByStudentUserIdOrderByIdDesc(studentId).stream().map(n -> row(n, Map.of())).toList());
        body.put("kinds", KINDS);
        body.put("max_length", MAX_LENGTH);
        return body;
    }

    /** Body: { kind UPDATE | THANK_YOU, message }. Goes to the office for approval. */
    @Transactional
    public StudentNote write(Long studentId, Map<String, Object> body) {
        GrantorGrantee mapping = currentSponsor(studentId)
                .orElseThrow(() -> bad("You don't have a sponsor at the moment, so there is no one to write to yet."));
        String kind = ServiceSupport.str(body.get("kind"));
        if (kind == null || !KINDS.containsKey(kind)) kind = "UPDATE";
        String message = ServiceSupport.str(body.get("message"));
        message = message == null ? "" : message.strip();
        if (message.length() < 10) throw bad("Please write a few words (at least 10 characters).");
        if (message.length() > MAX_LENGTH) throw bad("Please keep it under " + MAX_LENGTH + " characters.");
        if (notes.countByStudentUserIdAndStatus(studentId, StudentNote.PENDING) >= MAX_PENDING) {
            throw bad("You already have " + MAX_PENDING + " notes waiting for the office. Please wait until they are checked.");
        }
        StudentNote n = new StudentNote();
        n.setStudentUserId(studentId);
        n.setSponsorUserId(mapping.getGrantorId());
        n.setKind(kind);
        n.setMessage(message);
        n.setStatus(StudentNote.PENDING);
        notes.save(n);
        String name = users.findById(studentId).map(User::getName).orElse("A student");
        try {
            notifications.notifyRoles(reviewerRoles(), "Note to approve: " + name, name + " wrote a note to their sponsor.",
                    "STUDENT_NOTE", "/admin/student-notes");
        } catch (RuntimeException e) {
            // the note is saved; a missed notification doesn't undo it
        }
        return n;
    }

    // ---------------------------------------------------------------- office

    /** Notes of the students the user may see, newest first, optionally of one status; with counts by status. */
    @Transactional(readOnly = true)
    public Map<String, Object> list(String status) {
        StudentScope.Scope scope = studentScope.current();
        Map<Long, User> byId = new HashMap<>();
        List<StudentNote> all = notes.findAllByOrderByIdDesc();
        Set<Long> ids = new HashSet<>();
        all.forEach(n -> { ids.add(n.getStudentUserId()); ids.add(n.getSponsorUserId()); if (n.getReviewedBy() != null) ids.add(n.getReviewedBy()); });
        users.findAllById(ids).forEach(u -> byId.put(u.getId(), u));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : List.of(StudentNote.PENDING, StudentNote.APPROVED, StudentNote.RETURNED)) counts.put(s, 0L);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StudentNote n : all) {
            User student = byId.get(n.getStudentUserId());
            if (student == null || !scope.includes(student)) continue;
            counts.merge(n.getStatus(), 1L, Long::sum);
            if (status != null && !status.isBlank() && !status.equals(n.getStatus())) continue;
            rows.add(row(n, byId));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rows", rows);
        body.put("counts", counts);
        body.put("kinds", KINDS);
        return body;
    }

    /** Body: { decision APPROVE | RETURN, note (the reason, needed to return) }. */
    @Transactional
    public StudentNote review(Long id, Map<String, Object> body) {
        StudentNote n = notes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Note not found."));
        User student = studentScope.requireStudent(n.getStudentUserId());
        if (!StudentNote.PENDING.equals(n.getStatus())) throw bad("This note was already checked.");
        String decision = ServiceSupport.str(body.get("decision"));
        String reason = ServiceSupport.str(body.get("note"));
        reason = reason == null ? null : reason.strip();
        if ("RETURN".equals(decision)) {
            if (reason == null || reason.isEmpty()) throw bad("Please say why it is returned, so the student can fix it.");
            if (reason.length() > 500) throw bad("Please keep the reason under 500 characters.");
            n.setStatus(StudentNote.RETURNED);
            n.setReviewNote(reason);
        } else if ("APPROVE".equals(decision)) {
            n.setStatus(StudentNote.APPROVED);
            n.setReviewNote(null);
        } else {
            throw bad("Approve or return the note.");
        }
        n.setReviewedBy(Access.current().getUser().getId());
        n.setReviewedAt(LocalDateTime.now());
        notes.save(n);
        if (StudentNote.APPROVED.equals(n.getStatus())) {
            notifications.notify(n.getSponsorUserId(), "A note from " + student.getName(), shorten(n.getMessage()),
                    NotificationService.MAPPING, "/sponsor/dashboard", EmailType.SPONSOR_STUDENT_NOTE,
                    ServiceSupport.vars("student_name", student.getName(), "message", n.getMessage()));
        } else {
            notifications.notify(n.getStudentUserId(), "Your note was returned", "The office returned your note to your sponsor: " + reason,
                    "STUDENT_NOTE", "/student/notes", false);
        }
        return n;
    }

    // ---------------------------------------------------------------- sponsor

    /** Approved notes written to this sponsor, newest first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> forSponsor(Long sponsorId) {
        List<StudentNote> list = notes.findBySponsorUserIdAndStatusOrderByReviewedAtDesc(sponsorId, StudentNote.APPROVED);
        Map<Long, User> byId = new HashMap<>();
        users.findAllById(list.stream().map(StudentNote::getStudentUserId).distinct().toList()).forEach(u -> byId.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StudentNote n : list) {
            User s = byId.get(n.getStudentUserId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", n.getId());
            r.put("student_name", s == null ? null : s.getName());
            r.put("student_ref_id", s == null ? null : s.getUserId());
            r.put("kind", KINDS.getOrDefault(n.getKind(), n.getKind()));
            r.put("message", n.getMessage());
            r.put("date", n.getReviewedAt());
            rows.add(r);
        }
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    private Optional<GrantorGrantee> currentSponsor(Long studentId) {
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        return mappings.findFirstByGranteeId(studentId).filter(g -> g.getGrantorId() != null && !g.getGrantorId().equals(unassigned));
    }

    /** Roles that may approve notes (and the Super Admin). */
    private List<Integer> reviewerRoles() {
        List<Integer> out = new ArrayList<>();
        String key = Section.STUDENT_NOTES.key(Section.Level.EDIT);
        for (Role r : roles.findAll()) {
            if (Integer.valueOf(Role.SUPER_ADMIN).equals(r.getRoleId()) || r.permissionSet().contains(key)) out.add(r.getRoleId());
        }
        return out;
    }

    private Map<String, Object> row(StudentNote n, Map<Long, User> byId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", n.getId());
        r.put("kind", n.getKind());
        r.put("kind_label", KINDS.getOrDefault(n.getKind(), n.getKind()));
        r.put("message", n.getMessage());
        r.put("status", n.getStatus());
        r.put("written_at", n.getCreatedAt());
        r.put("reviewed_at", n.getReviewedAt());
        r.put("review_note", n.getReviewNote());
        if (!byId.isEmpty()) {
            User s = byId.get(n.getStudentUserId()), sp = byId.get(n.getSponsorUserId()), rv = n.getReviewedBy() == null ? null : byId.get(n.getReviewedBy());
            r.put("student_id", n.getStudentUserId());
            r.put("student_ref_id", s == null ? null : s.getUserId());
            r.put("student_name", s == null ? null : s.getName());
            r.put("chapter", s == null ? null : s.getChapterName());
            r.put("sponsor_id", sp == null ? null : sp.getUserId());
            r.put("sponsor_name", sp == null ? null : sp.getName());
            r.put("reviewed_by", rv == null ? null : rv.getName());
        }
        return r;
    }

    private static String shorten(String s) {
        return s.length() > 160 ? s.substring(0, 157) + "…" : s;
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
