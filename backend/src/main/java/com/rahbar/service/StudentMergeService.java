package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Merges a duplicate student account into the one to keep (Super Admin only). The duplicate's applications,
 * payments, progress reports, notifications and study-status history move to the kept account; its course, bank
 * details, sponsor and alumni profile move only when the kept account has none (otherwise the kept account's stay).
 * The duplicate is deactivated and marked as merged; it is never deleted. History that belongs to the account
 * itself (activity log, sign-in codes, email log) stays where it is.
 */
@Service
public class StudentMergeService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final PaymentInstallmentService installmentService;

    public StudentMergeService(UserRepository userRepository, JdbcTemplate jdbc, PaymentInstallmentService installmentService) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.installmentService = installmentService;
    }

    /** What the merge would do: counts that move, and what stays because the kept account already has one. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long keepId, Long removeId) {
        User keep = require(keepId), remove = require(removeId);
        check(keep, remove);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keep", card(keep));
        body.put("remove", card(remove));
        Map<String, Object> moves = new LinkedHashMap<>();
        moves.put("applications", count("grantee_details", "user_id", removeId));
        moves.put("payments", count("payments", "grantee_id", removeId));
        moves.put("progressReports", count("student_progress", "grantee_id", removeId));
        moves.put("notifications", count("notifications", "user_id", removeId));
        moves.put("statusHistory", count("student_status_history", "user_id", removeId));
        body.put("moves", moves);
        List<String> notes = new ArrayList<>();
        note(notes, "student_institution_courses", "user_id", keepId, removeId, "course");
        note(notes, "bank_details", "user_id", keepId, removeId, "bank details");
        note(notes, "grantor_grantees", "grantee_id", keepId, removeId, "sponsor mapping");
        note(notes, "alumni_profiles", "user_id", keepId, removeId, "alumni profile");
        body.put("notes", notes);
        return body;
    }

    @Transactional
    public Map<String, Object> merge(Long keepId, Long removeId) {
        User keep = require(keepId), remove = require(removeId);
        check(keep, remove);

        moveAll("grantee_details", "user_id", keepId, removeId);
        moveAll("payments", "grantee_id", keepId, removeId);
        moveAll("student_progress", "grantee_id", keepId, removeId);
        moveAll("notifications", "user_id", keepId, removeId);
        moveAll("student_status_history", "user_id", keepId, removeId);
        moveIfMissing("student_institution_courses", "user_id", keepId, removeId);
        moveIfMissing("bank_details", "user_id", keepId, removeId);
        moveIfMissing("alumni_profiles", "user_id", keepId, removeId);
        if (count("grantor_grantees", "grantee_id", keepId) == 0) {
            moveAll("grantor_grantees", "grantee_id", keepId, removeId);
        } else {
            jdbc.update("DELETE FROM grantor_grantees WHERE grantee_id = ?", removeId);
        }
        // Installments are rebuilt from the kept account's data; the moved Paid payments pay them again.
        jdbc.update("DELETE FROM payment_installments WHERE grantee_id = ?", removeId);

        // Fill in what the kept account doesn't have yet.
        if (keep.getYear() == null) keep.setYear(remove.getYear());
        if (keep.getPaymentStartDate() == null) keep.setPaymentStartDate(remove.getPaymentStartDate());
        if (keep.getChapterId() == null) keep.setChapterId(remove.getChapterId());
        if (isBlank(keep.getPhone())) keep.setPhone(remove.getPhone());
        if (!usableEmail(keep.getEmail()) && usableEmail(remove.getEmail())) {
            String email = remove.getEmail();
            remove.setEmail(null); // the address moves to the kept account
            userRepository.saveAndFlush(remove);
            keep.setEmail(email);
        }
        userRepository.save(keep);

        remove.setStatus("Inactive");
        remove.setMergedIntoId(keepId);
        remove.setStudyStatusNote(trim("Merged into " + keep.getUserId() + " (" + keep.getName() + ")"));
        userRepository.save(remove);
        userRepository.flush();

        installmentService.sync(keepId);
        installmentService.sync(removeId);
        return Map.of("message", remove.getUserId() + " was merged into " + keep.getUserId() + ". " + remove.getUserId()
                + " is now inactive and marked as merged.");
    }

    private void check(User keep, User remove) {
        if (!Access.isSuperAdmin()) throw Access.forbidden("Only the Super Admin can merge students.");
        if (keep.getId().equals(remove.getId())) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose two different students.");
        for (User u : List.of(keep, remove)) {
            if (!Integer.valueOf(ServiceSupport.STUDENT_ROLE).equals(u.getRoleId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, u.getUserId() + " is not a student.");
            }
            if (u.getMergedIntoId() != null) throw new ApiException(HttpStatus.BAD_REQUEST, u.getUserId() + " was already merged into another student.");
        }
    }

    private User require(Long id) {
        if (id == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose both students.");
        return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found."));
    }

    private long count(String table, String column, Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
        return n == null ? 0 : n;
    }

    private void moveAll(String table, String column, Long keepId, Long removeId) {
        jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?", keepId, removeId);
    }

    /** Moves the duplicate's row only when the kept account has none (one row per student tables). */
    private void moveIfMissing(String table, String column, Long keepId, Long removeId) {
        if (count(table, column, keepId) == 0) moveAll(table, column, keepId, removeId);
    }

    private void note(List<String> notes, String table, String column, Long keepId, Long removeId, String what) {
        long keepHas = count(table, column, keepId), removeHas = count(table, column, removeId);
        if (removeHas == 0) return;
        notes.add(keepHas == 0 ? "The duplicate's " + what + " moves to the kept student."
                : "Both have a " + what + "; the kept student's " + what + " stays and the duplicate's is "
                + (table.equals("grantor_grantees") ? "removed." : "left on the inactive account."));
    }

    private Map<String, Object> card(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("user_id", u.getUserId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("status", u.getStatus());
        m.put("chapter", u.getChapterName());
        m.put("created_at", u.getCreatedAt());
        return m;
    }

    private static boolean usableEmail(String e) {
        return e != null && e.contains("@") && !e.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
