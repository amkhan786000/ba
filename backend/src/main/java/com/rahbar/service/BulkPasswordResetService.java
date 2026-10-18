package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Administration > Bulk Password Reset (Super Admin only): a new temporary password for everyone in a role (students:
 * of the chosen session years), emailed to each of them; they choose their own at the next sign-in. "Who" narrows it:
 * everyone, only those still on a temporary password, or only those who already set their own.
 * <p>
 * Who is left out: inactive and merged accounts, the "unassigned" placeholder sponsor, the Super Admin doing it, and
 * anyone without a real email address (they would be locked out with no way to get the new password; they are listed
 * instead). A password only changes once its email has gone out, so a failed email leaves the old password working.
 * Emails go out one after another in the background (hundreds take minutes); the page follows {@link #status()}.
 * <p>
 * {@link #temporaryPasswords()} shows who has set their own password since (users.must_change_password is set with
 * every temporary password and cleared when the user chooses theirs).
 */
@Service
public class BulkPasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(BulkPasswordResetService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final TrustedDeviceService trustedDevices;
    /** One reset at a time. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bulk-password-reset");
        t.setDaemon(true);
        return t;
    });
    /** The current or last run (null before the first). */
    private volatile Job job;

    public BulkPasswordResetService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                                    EmailService emailService, NotificationService notificationService,
                                    TrustedDeviceService trustedDevices) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.trustedDevices = trustedDevices;
    }

    /** A run: counts while it goes, then who was skipped or whose email failed. */
    static final class Job {
        final String roleName;
        final List<Integer> years;
        final LocalDateTime startedAt = LocalDateTime.now();
        final int total;
        volatile int done, reset;
        /** users.id of the accounts that got a new temporary password in this run. */
        final List<Long> resetIds = Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, Object>> failed = Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, Object>> skipped;
        volatile LocalDateTime finishedAt;

        Job(String roleName, List<Integer> years, int total, List<Map<String, Object>> skipped) {
            this.roleName = roleName;
            this.years = years;
            this.total = total;
            this.skipped = skipped;
        }
    }

    /** Roles with how many accounts each would reset, and the students' session years with their counts. */
    public Map<String, Object> options() {
        requireSuperAdmin();
        Long me = Access.current().getUser().getId();
        List<Map<String, Object>> roleRows = new ArrayList<>();
        for (Role r : roles.findAll()) {
            List<User> in = candidates(r.getRoleId(), null, me);
            roleRows.add(Map.of("role_id", r.getRoleId(), "role_name", r.getRoleName(), "accounts", in.size()));
        }
        roleRows.sort(Comparator.comparing(m -> String.valueOf(m.get("role_name")), String.CASE_INSENSITIVE_ORDER));
        Map<Integer, Integer> years = new TreeMap<>(Comparator.reverseOrder());
        for (User u : candidates(ServiceSupport.STUDENT_ROLE, null, me)) if (u.getYear() != null) years.merge(u.getYear(), 1, Integer::sum);
        List<Map<String, Object>> yearRows = new ArrayList<>();
        years.forEach((y, n) -> yearRows.add(Map.of("year", y, "students", n)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roles", roleRows);
        body.put("student_years", yearRows);
        body.put("student_role_id", ServiceSupport.STUDENT_ROLE);
        body.put("running", running());
        return body;
    }

    /** Who would get a new password, and who would be skipped (no real email). Body: { roleId, years: [2025, ...] }. */
    public Map<String, Object> preview(Map<String, Object> body) {
        requireSuperAdmin();
        Selection sel = selection(body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role_name", sel.roleName());
        out.put("years", sel.years());
        out.put("who", WHO.get(sel.who()));
        out.put("to_reset", sel.toReset().size());
        out.put("skipped", sel.skipped());
        return out;
    }

    /** Starts the reset in the background; {@link #status()} follows it. */
    public Map<String, Object> start(Map<String, Object> body) {
        requireSuperAdmin();
        if (running()) throw new ApiException(HttpStatus.CONFLICT, "A bulk password reset is already running.");
        Selection sel = selection(body);
        if (sel.toReset().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Nobody to reset: no active account with a real email in that selection.");
        Job j = new Job(sel.roleName() + " · " + WHO.get(sel.who()).toLowerCase(Locale.ROOT), sel.years(), sel.toReset().size(), sel.skipped());
        job = j;
        List<Long> ids = sel.toReset().stream().map(User::getId).toList();
        Long by = Access.current().getUser().getId();
        log.info("Bulk password reset started by user {}: {} account(s) in {}{}", by, ids.size(), sel.roleName(),
                sel.years().isEmpty() ? "" : " " + sel.years());
        worker.submit(() -> run(j, ids));
        return status();
    }

    /** The current or last run. */
    public Map<String, Object> status() {
        requireSuperAdmin();
        Job j = job;
        Map<String, Object> out = new LinkedHashMap<>();
        if (j == null) { out.put("state", "none"); return out; }
        out.put("state", j.finishedAt == null ? "running" : "finished");
        out.put("role_name", j.roleName);
        out.put("years", j.years);
        out.put("total", j.total);
        out.put("done", j.done);
        out.put("reset", j.reset);
        out.put("failed", List.copyOf(j.failed));
        out.put("skipped", j.skipped);
        out.put("started_at", j.startedAt);
        out.put("finished_at", j.finishedAt);
        // of the accounts reset in this run, how many have chosen their own password since
        List<Long> ids = List.copyOf(j.resetIds);
        long changed = ids.isEmpty() ? 0 : users.findAllById(ids).stream().filter(u -> !Boolean.TRUE.equals(u.getMustChangePassword())).count();
        out.put("changed_since", changed);
        return out;
    }

    /**
     * Who still signs in with a temporary password (users.must_change_password): per role, active accounts and how
     * many have set their own; students also per session year; and the people still on a temporary one.
     */
    public Map<String, Object> temporaryPasswords() {
        requireSuperAdmin();
        Map<Integer, String> roleNames = new HashMap<>();
        roles.findAll().forEach(r -> roleNames.put(r.getRoleId(), r.getRoleName()));
        List<User> active = users.findAll().stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null)
                .toList();
        Map<Integer, int[]> byRole = new TreeMap<>(); // [accounts, still temporary]
        Map<Integer, int[]> byYear = new TreeMap<>(Comparator.reverseOrder());
        List<Map<String, Object>> still = new ArrayList<>();
        for (User u : active) {
            boolean temp = Boolean.TRUE.equals(u.getMustChangePassword());
            int[] r = byRole.computeIfAbsent(u.getRoleId(), k -> new int[2]);
            r[0]++;
            if (temp) r[1]++;
            if (Integer.valueOf(ServiceSupport.STUDENT_ROLE).equals(u.getRoleId()) && u.getYear() != null) {
                int[] y = byYear.computeIfAbsent(u.getYear(), k -> new int[2]);
                y[0]++;
                if (temp) y[1]++;
            }
            if (temp) {
                Map<String, Object> m = person(u, null);
                m.remove("why");
                m.put("role", roleNames.getOrDefault(u.getRoleId(), "role " + u.getRoleId()));
                m.put("session_year", u.getYear());
                still.add(m);
            }
        }
        List<Map<String, Object>> roleRows = new ArrayList<>();
        byRole.forEach((id, c) -> roleRows.add(Map.of("role", roleNames.getOrDefault(id, "role " + id), "accounts", c[0],
                "own_password", c[0] - c[1], "temporary", c[1])));
        List<Map<String, Object>> yearRows = new ArrayList<>();
        byYear.forEach((y, c) -> yearRows.add(Map.of("session_year", y, "students", c[0], "own_password", c[0] - c[1], "temporary", c[1])));
        still.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("role")))
                .thenComparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("by_role", roleRows);
        body.put("students_by_year", yearRows);
        body.put("still_temporary", still);
        return body;
    }

    private boolean running() {
        Job j = job;
        return j != null && j.finishedAt == null;
    }

    private void run(Job j, List<Long> ids) {
        try {
            for (Long id : ids) {
                try {
                    users.findById(id).ifPresent(u -> {
                        if (resetOne(u)) { j.reset++; j.resetIds.add(u.getId()); }
                        else j.failed.add(person(u, "The email could not be sent; the old password still works."));
                    });
                } catch (RuntimeException e) {
                    log.warn("Bulk password reset: user {} failed: {}", id, e.getMessage());
                    users.findById(id).ifPresent(u -> j.failed.add(person(u, "Error: " + e.getMessage())));
                }
                j.done++;
            }
        } finally {
            j.finishedAt = LocalDateTime.now();
            log.info("Bulk password reset finished: {} reset, {} failed, {} skipped", j.reset, j.failed.size(), j.skipped.size());
        }
    }

    /** Emails a new temporary password, then (only if it went out) saves it; the user picks their own at sign-in. */
    private boolean resetOne(User u) {
        String password = PasswordPolicy.temporaryPassword();
        boolean emailed = emailService.sendWithSecret(EmailType.PASSWORD_RESET_BY_ADMIN, u.getEmail().trim(),
                ServiceSupport.vars("name", u.getName(), "password", password), password);
        if (!emailed) return false;
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        trustedDevices.forgetAll(u.getId()); // remembered browsers must use a code again
        notificationService.notify(u.getId(), "Password reset",
                "An administrator reset your password. Use the temporary password emailed to you and choose a new one.",
                NotificationService.ACCOUNT, null, false);
        return true;
    }

    private record Selection(String roleName, List<Integer> years, String who, List<User> toReset, List<Map<String, Object>> skipped) {}

    /** Who in the role: everyone, only those still on a temporary password, only those who set their own. */
    static final Map<String, String> WHO = new LinkedHashMap<>(Map.of(
            "ALL", "All users", "TEMPORARY", "Only those still on a temporary password", "OWN", "Only those who set their own password"));

    private Selection selection(Map<String, Object> body) {
        Object r = body == null ? null : body.get("roleId");
        if (r == null || String.valueOf(r).isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a role.");
        int roleId = Integer.parseInt(String.valueOf(r).trim());
        Role role = roles.findById(roleId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown role."));
        List<Integer> years = new ArrayList<>();
        if (roleId == ServiceSupport.STUDENT_ROLE) {
            if (body.get("years") instanceof Collection<?> c) c.forEach(y -> years.add(Integer.parseInt(String.valueOf(y).trim())));
            if (years.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose at least one session year.");
            Collections.sort(years);
        }
        String who = body.get("who") == null || String.valueOf(body.get("who")).isBlank() ? "ALL" : String.valueOf(body.get("who")).trim().toUpperCase(Locale.ROOT);
        if (!WHO.containsKey(who)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose who to reset: all, temporary password or own password.");
        List<User> toReset = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (User u : candidates(roleId, years.isEmpty() ? null : Set.copyOf(years), Access.current().getUser().getId())) {
            boolean temporary = Boolean.TRUE.equals(u.getMustChangePassword());
            if ("TEMPORARY".equals(who) && !temporary || "OWN".equals(who) && temporary) continue;
            if (usableEmail(u.getEmail())) toReset.add(u);
            else skipped.add(person(u, "No real email address (blank or a @rahbar.com placeholder)."));
        }
        return new Selection(role.getRoleName(), years, who, toReset, skipped);
    }

    /** Active accounts with the role (main or extra; students: of these session years), minus who is never reset. */
    private List<User> candidates(int roleId, Set<Integer> years, Long me) {
        return users.findByRoleId(roleId).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null)
                .filter(u -> !u.getId().equals(me) && !ServiceSupport.UNASSIGNED_GRANTOR_CODE.equals(u.getUserId()))
                .filter(u -> years == null || (u.getYear() != null && years.contains(u.getYear())))
                .sorted(Comparator.comparing(u -> String.valueOf(u.getName()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Map<String, Object> person(User u, String why) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("user_id", u.getUserId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("why", why);
        return m;
    }

    private static boolean usableEmail(String email) {
        return email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    private static void requireSuperAdmin() {
        if (!Access.isSuperAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Only the Super Admin can reset passwords in bulk.");
    }
}
