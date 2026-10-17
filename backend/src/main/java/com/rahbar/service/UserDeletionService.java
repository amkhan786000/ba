package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.util.UserRoles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Users > Edit > Delete (Super Admin only): a user can be deleted only when nothing in Rahbar is linked to them: no
 * students or sponsor, payments, installments, applications, course, progress, chapter or RCC centre, and nothing
 * they created or changed (any other record that points to them). Otherwise the reasons are listed and the account
 * can be deactivated instead.
 * <p>
 * What only belongs to the sign-in itself goes with the user (sign-in codes, remembered devices, push subscriptions,
 * notifications, extra roles); the activity and email logs keep their rows (with the name / address as text) and
 * only lose the link.
 */
@Service
public class UserDeletionService {

    /** The main links, with a readable reason: "table.column" -> what it means. Checked first, in this order. */
    private static final Map<String, String> LINKS = new LinkedHashMap<>();
    static {
        LINKS.put("grantor_grantees.grantor_id", "student(s) mapped to them as sponsor");
        LINKS.put("grantor_grantees.grantee_id", "sponsor mapping (as a student)");
        LINKS.put("payments.grantor_id", "payment(s) made as sponsor");
        LINKS.put("payments.grantee_id", "payment(s) received as student");
        LINKS.put("payment_installments.grantor_id", "installment(s) as sponsor");
        LINKS.put("payment_installments.grantee_id", "installment(s) as student");
        LINKS.put("sponsor_mapping_history.grantor_id", "sponsorship history as sponsor");
        LINKS.put("sponsor_mapping_history.grantee_id", "sponsorship history as student");
        LINKS.put("scholarship_applications.student_user_id", "scholarship application(s)");
        LINKS.put("grantee_details.user_id", "application / family details");
        LINKS.put("student_institution_courses.user_id", "a course assigned");
        LINKS.put("student_progress.grantee_id", "progress report(s)");
        LINKS.put("bank_details.user_id", "bank details");
        LINKS.put("alumni_profiles.user_id", "an alumni profile");
        LINKS.put("student_status_history.user_id", "study status history");
        LINKS.put("online_payments.sponsor_user_id", "online payment(s)");
        LINKS.put("reb_sponsorships.sponsor_user_id", "Rahbar-e-Banaat sponsorship(s)");
        LINKS.put("reb_payments.sponsor_user_id", "Rahbar-e-Banaat fee payment(s)");
        LINKS.put("student_notes.sponsor_user_id", "notes from students");
        LINKS.put("student_notes.student_user_id", "notes to a sponsor");
        LINKS.put("mentorships.student_user_id", "mentoring (as student)");
        LINKS.put("mentorships.alumni_user_id", "mentoring (as mentor)");
        LINKS.put("sponsor_followups.sponsor_user_id", "sponsor follow-ups");
        LINKS.put("users.merged_into_id", "account(s) merged into this one");
    }

    /** Deleted with the user: they only belong to the sign-in. */
    private static final List<String> REMOVED_WITH_USER = List.of("trusted_devices.user_id", "password_reset_codes.user_id",
            "otp.user_id", "push_subscriptions.user_id", "notifications.user_id", "user_additional_roles.user_id");

    /** History that stays, with the user's name / address kept as text; only the link to the user is cleared. */
    private static final List<String> LINK_CLEARED = List.of("activity_log.user_id", "email_log.recipient_user_id");

    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public UserDeletionService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** Why the user can't be deleted (empty when they can): "3 student(s) mapped to them as sponsor", ... */
    @Transactional(readOnly = true)
    public List<String> blockers(Long id) {
        requireSuperAdmin();
        User u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        List<String> out = new ArrayList<>();
        if (Access.current().getUser().getId().equals(id)) out.add("This is your own account.");
        if (ServiceSupport.UNASSIGNED_GRANTOR_CODE.equals(u.getUserId())) out.add("This is the \"unassigned\" placeholder sponsor Rahbar needs.");
        if (UserRoles.has(u, Role.SUPER_ADMIN, users) && superAdmins() <= 1) out.add("This is the only Super Admin.");
        if (u.getChapterId() != null) out.add("Belongs to a chapter" + (u.getChapterName() == null ? "" : " (" + u.getChapterName() + ")") + ": remove the chapter first.");
        if (u.getRccCenterId() != null) out.add("Belongs to an RCC centre: remove it first.");

        Set<String> columns = columns();
        Set<String> checked = new HashSet<>();
        for (Map.Entry<String, String> link : LINKS.entrySet()) {
            checked.add(link.getKey());
            if (!columns.contains(link.getKey())) continue;
            long n = count(link.getKey(), id);
            if (n > 0) out.add(n + " " + link.getValue());
        }
        // Anything else pointing to the user (mostly "created by" / "changed by"): their work stays linked to them.
        Map<String, Long> other = new TreeMap<>();
        for (String fk : foreignKeys()) {
            if (checked.contains(fk) || REMOVED_WITH_USER.contains(fk) || LINK_CLEARED.contains(fk)) continue;
            long n = count(fk, id);
            if (n > 0) other.merge(fk.substring(0, fk.indexOf('.')).replace('_', ' '), n, Long::sum);
        }
        other.forEach((table, n) -> out.add(n + " " + table + " record(s) they created or changed"));
        return out;
    }

    /** Deletes the user when nothing is linked to them (else 400 with the reasons). */
    @Transactional
    public void delete(Long id) {
        List<String> why = blockers(id);
        if (!why.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This user can't be deleted: " + String.join("; ", why)
                    + ". Deactivate the account instead.");
        }
        Set<String> columns = columns();
        for (String c : LINK_CLEARED) {
            if (columns.contains(c)) jdbc.update("UPDATE " + table(c) + " SET " + column(c) + " = NULL WHERE " + column(c) + " = ?", id);
        }
        for (String c : REMOVED_WITH_USER) {
            if (columns.contains(c)) jdbc.update("DELETE FROM " + table(c) + " WHERE " + column(c) + " = ?", id);
        }
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    private static void requireSuperAdmin() {
        if (!Access.isSuperAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Only the Super Admin can delete users.");
    }

    private long superAdmins() {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.id) FROM users u LEFT JOIN user_additional_roles r ON r.user_id = u.id
                WHERE (u.role_id = ? OR r.role_id = ?) AND u.status = 'Active'""", Long.class, Role.SUPER_ADMIN, Role.SUPER_ADMIN);
        return n == null ? 0 : n;
    }

    /** Rows whose column points to the user; on the users table the user's own row doesn't count. */
    private long count(String tableColumn, Long id) {
        boolean self = tableColumn.startsWith("users.");
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(tableColumn) + " WHERE " + column(tableColumn) + " = ?"
                + (self ? " AND id <> ?" : ""), Long.class, self ? new Object[]{id, id} : new Object[]{id});
        return n == null ? 0 : n;
    }

    /** Every "table.column" of this database (names from information_schema only, never from a request). */
    private Set<String> columns() {
        return new HashSet<>(jdbc.queryForList("""
                SELECT CONCAT(LOWER(table_name), '.', LOWER(column_name)) FROM information_schema.columns
                WHERE table_schema = DATABASE()""", String.class));
    }

    /** Every foreign key column that points to users.id. */
    private List<String> foreignKeys() {
        return jdbc.queryForList("""
                SELECT DISTINCT CONCAT(LOWER(table_name), '.', LOWER(column_name)) FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND referenced_table_name = 'users' AND referenced_column_name = 'id'""", String.class);
    }

    private static String table(String tableColumn) { return "`" + tableColumn.substring(0, tableColumn.indexOf('.')) + "`"; }
    private static String column(String tableColumn) { return "`" + tableColumn.substring(tableColumn.indexOf('.') + 1) + "`"; }
}
