package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.util.UserRoles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Merges a person's second account into the one to keep (Super Admin only), e.g. a staff member who once got a
 * separate sponsor account. One person is one user record with several roles, so the kept account gets the other
 * account's roles as additional roles, and everything that belongs to the person moves to it: what they sponsor
 * (students, payments, installments, mapping history, follow-ups, REB sponsorships), what they have as a student
 * (applications, payments, progress, course, bank details, alumni profile, mentoring), notifications, chats, approvals
 * and interview reviews. History that belongs to the account itself (activity log, email log, sign-in codes, who
 * created / changed records) stays. The second account is deactivated and marked as merged; it is never deleted.
 * Two student accounts of the same student are merged with the student merge instead.
 */
@Service
public class PersonMergeService {

    /** [table, column, what] moved whole. */
    private static final String[][] MOVE_ALL = {
            {"grantor_grantees", "grantor_id", "sponsored student(s)"},
            {"payments", "grantor_id", "payment(s) made as sponsor"},
            {"payment_installments", "grantor_id", "installment(s) as sponsor"},
            {"sponsor_mapping_history", "grantor_id", "sponsorship history row(s)"},
            {"sponsor_followups", "sponsor_user_id", "sponsor follow-up(s)"},
            {"reb_sponsorships", "sponsor_user_id", "Rahbar-e-Banaat sponsorship(s)"},
            {"reb_payments", "sponsor_user_id", "Rahbar-e-Banaat payment(s) as sponsor"},
            {"grantee_details", "user_id", "application(s)"},
            {"payments", "grantee_id", "payment(s) received as student"},
            {"student_progress", "grantee_id", "progress report(s)"},
            {"student_status_history", "user_id", "study-status change(s)"},
            {"sponsor_mapping_history", "grantee_id", "student sponsorship history row(s)"},
            {"mentorships", "student_user_id", "mentoring pair(s) as student"},
            {"mentorships", "alumni_user_id", "mentoring pair(s) as mentor"},
            {"reb_students", "rahbar_user_id", "linked Rahbar-e-Banaat record(s)"},
            {"notifications", "user_id", "notification(s)"},
            {"push_subscriptions", "user_id", "phone / browser notification subscription(s)"},
            {"chats", "sender_id", "chat message(s) sent"},
            {"chats", "receiver_id", "chat message(s) received"},
            {"approvals", "approver_id", "approval(s)"},
    };

    /** [table, column, what]: one row per person; moves only when the kept account has none. */
    private static final String[][] MOVE_IF_MISSING = {
            {"student_institution_courses", "user_id", "course"},
            {"bank_details", "user_id", "bank details"},
            {"alumni_profiles", "user_id", "alumni profile"},
            {"reb_staff", "user_id", "Rahbar-e-Banaat staff record"},
    };

    private final UserRepository users;
    private final RoleRepository roles;
    private final JdbcTemplate jdbc;
    private final AdditionalRoleService additionalRoles;
    private final PaymentInstallmentService installments;

    public PersonMergeService(UserRepository users, RoleRepository roles, JdbcTemplate jdbc, AdditionalRoleService additionalRoles,
                              PaymentInstallmentService installments) {
        this.users = users;
        this.roles = roles;
        this.jdbc = jdbc;
        this.additionalRoles = additionalRoles;
        this.installments = installments;
    }

    /** What the merge would do: { keep, remove, roles, lines (what moves), notes (what stays) }. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long keepId, Long removeId) {
        User keep = require(keepId), remove = require(removeId);
        check(keep, remove);
        List<String> lines = new ArrayList<>();
        for (String[] m : MOVE_ALL) {
            long n = count(m[0], m[1], removeId);
            if (n > 0) lines.add(n + " " + m[2]);
        }
        long reviews = movableReviews(keepId, removeId);
        if (reviews > 0) lines.add(reviews + " interview review(s)");
        List<String> notes = new ArrayList<>();
        for (String[] m : MOVE_IF_MISSING) {
            if (count(m[0], m[1], removeId) == 0) continue;
            if (count(m[0], m[1], keepId) == 0) lines.add("the " + m[2]);
            else notes.add("Both accounts have a " + m[2] + "; " + keep.getUserId() + "'s stays and the other one is left on the inactive account.");
        }
        if (count("grantor_grantees", "grantee_id", removeId) > 0) {
            if (count("grantor_grantees", "grantee_id", keepId) == 0) lines.add("their sponsor (as a student)");
            else notes.add("Both accounts have a sponsor as a student; " + keep.getUserId() + "'s stays and the other mapping is removed.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keep", card(keep));
        body.put("remove", card(remove));
        body.put("roles", roleNames(rolesAfter(keep, remove)));
        body.put("lines", lines);
        body.put("notes", notes);
        return body;
    }

    @Transactional
    public Map<String, Object> merge(Long keepId, Long removeId) {
        User keep = require(keepId), remove = require(removeId);
        check(keep, remove);
        Set<Integer> rolesAfter = rolesAfter(keep, remove);
        boolean student = UserRoles.has(remove, ServiceSupport.STUDENT_ROLE, users);

        for (String[] m : MOVE_ALL) moveAll(m[0], m[1], keepId, removeId);
        jdbc.update("UPDATE interview_reviews r SET interviewer_id = ? WHERE interviewer_id = ? AND NOT EXISTS ("
                + "SELECT 1 FROM (SELECT grantee_detail_id, application_id FROM interview_reviews WHERE interviewer_id = ?) k "
                + "WHERE k.grantee_detail_id <=> r.grantee_detail_id AND k.application_id <=> r.application_id)",
                keepId, removeId, keepId);
        for (String[] m : MOVE_IF_MISSING) if (count(m[0], m[1], keepId) == 0) moveAll(m[0], m[1], keepId, removeId);
        if (count("grantor_grantees", "grantee_id", keepId) == 0) moveAll("grantor_grantees", "grantee_id", keepId, removeId);
        else jdbc.update("DELETE FROM grantor_grantees WHERE grantee_id = ?", removeId);
        if (student) jdbc.update("DELETE FROM payment_installments WHERE grantee_id = ?", removeId); // rebuilt for the kept account

        // Details the kept account doesn't have yet (email / phone are unique: free them on the other account first).
        if (keep.getChapterId() == null) keep.setChapterId(remove.getChapterId());
        if (keep.getRccCenterId() == null) keep.setRccCenterId(remove.getRccCenterId());
        if (student) {
            if (keep.getYear() == null) keep.setYear(remove.getYear());
            if (keep.getPaymentStartDate() == null) keep.setPaymentStartDate(remove.getPaymentStartDate());
            if (keep.getStudyStatus() == null) keep.setStudyStatus(remove.getStudyStatus());
        }
        String phone = blank(keep.getPhone()) && !blank(remove.getPhone()) ? remove.getPhone() : null;
        String email = !usableEmail(keep.getEmail()) && usableEmail(remove.getEmail()) ? remove.getEmail() : null;
        if (phone != null) remove.setPhone(null);
        if (email != null) remove.setEmail(null);
        remove.setStatus("Inactive");
        remove.setMergedIntoId(keepId);
        users.saveAndFlush(remove);
        if (phone != null) keep.setPhone(phone);
        if (email != null) keep.setEmail(email);
        users.save(keep);
        additionalRoles.set(keep, rolesAfter);
        additionalRoles.set(remove, List.of());
        users.flush();

        if (student) {
            installments.sync(keepId);
            installments.sync(removeId);
        }
        return Map.of("message", remove.getUserId() + " was merged into " + keep.getUserId() + ", who now has the roles "
                + String.join(", ", roleNames(rolesAfter)) + ". " + remove.getUserId() + " is now inactive and marked as merged.");
    }

    // ---------------------------------------------------------------- helpers

    private void check(User keep, User remove) {
        if (!Access.isSuperAdmin()) throw Access.forbidden("Only the Super Admin can merge accounts.");
        if (keep.getId().equals(remove.getId())) throw bad("Choose two different accounts.");
        for (User u : List.of(keep, remove)) {
            if (u.getMergedIntoId() != null) throw bad(u.getUserId() + " was already merged into another account.");
        }
        if (UserRoles.has(keep, ServiceSupport.STUDENT_ROLE, users) && UserRoles.has(remove, ServiceSupport.STUDENT_ROLE, users)) {
            throw bad("Both are student accounts: use the student merge (Data Quality > Possible duplicate students), which also sorts out courses and bank details.");
        }
    }

    /** The kept account's roles plus the other account's; the kept account's main role stays its main role. */
    private Set<Integer> rolesAfter(User keep, User remove) {
        Set<Integer> out = new TreeSet<>(additionalRoles.of(keep.getId()));
        out.add(remove.getRoleId());
        out.addAll(additionalRoles.of(remove.getId()));
        out.remove(keep.getRoleId());
        Set<Integer> all = new LinkedHashSet<>();
        all.add(keep.getRoleId());
        all.addAll(out);
        if (all.contains(Role.SUPER_ADMIN) && !Access.isSuperAdmin()) throw Access.forbidden("Only a Super Admin can manage Super Admin accounts.");
        return all;
    }

    private List<String> roleNames(Collection<Integer> ids) {
        Map<Integer, String> names = new HashMap<>();
        roles.findAll().forEach(r -> names.put(r.getRoleId(), r.getRoleName()));
        return ids.stream().map(id -> names.getOrDefault(id, "role " + id)).toList();
    }

    private long movableReviews(Long keepId, Long removeId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM interview_reviews r WHERE interviewer_id = ? AND NOT EXISTS ("
                + "SELECT 1 FROM interview_reviews k WHERE k.interviewer_id = ? AND k.grantee_detail_id <=> r.grantee_detail_id "
                + "AND k.application_id <=> r.application_id)", Long.class, removeId, keepId);
        return n == null ? 0 : n;
    }

    private User require(Long id) {
        if (id == null) throw bad("Choose both accounts.");
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    private long count(String table, String column, Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
        return n == null ? 0 : n;
    }

    private void moveAll(String table, String column, Long keepId, Long removeId) {
        jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?", keepId, removeId);
    }

    private Map<String, Object> card(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("user_id", u.getUserId());
        m.put("name", u.getName());
        m.put("status", u.getStatus());
        m.put("chapter", u.getChapterName());
        m.put("created_at", u.getCreatedAt());
        return m;
    }

    private static boolean usableEmail(String e) {
        return e != null && e.contains("@") && !e.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
