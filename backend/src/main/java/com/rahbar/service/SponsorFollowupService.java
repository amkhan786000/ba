package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * Sponsors & Payments > Sponsor Follow-up: keeping sponsors.
 * <ul>
 *   <li>Lapsed: sponsors with installments unpaid for more than N days (default 30) after their due date.</li>
 *   <li>Available: active sponsors with no student now (their student graduated, left or moved), or who never had one.</li>
 * </ul>
 * The office records calls and notes, or sends a personal email ("a gentle reminder" / "would you sponsor another
 * student?", both editable in Email Templates). Sponsors are shown by name and ID only; the server uses their email
 * address, nobody sees it. A sponsor contacted in the last 7 days is skipped unless the office insists.
 */
@Service
public class SponsorFollowupService {

    static final Set<String> KINDS = Set.of("LAPSED", "AVAILABLE");
    static final int QUIET_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final UserRepository users;
    private final PaymentInstallmentRepository installments;
    private final GrantorGranteeRepository mappings;
    private final SponsorMappingHistoryRepository history;
    private final SponsorFollowupRepository followups;
    private final EmailService email;

    public SponsorFollowupService(UserRepository users, PaymentInstallmentRepository installments, GrantorGranteeRepository mappings,
                                  SponsorMappingHistoryRepository history, SponsorFollowupRepository followups, EmailService email) {
        this.users = users;
        this.installments = installments;
        this.mappings = mappings;
        this.history = history;
        this.followups = followups;
        this.email = email;
    }

    /** Active sponsors by users.id. */
    private Map<Long, User> sponsors() {
        Map<Long, User> out = new LinkedHashMap<>();
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        for (User u : users.findByRoleIdIn(ServiceSupport.SPONSOR_ROLES)) {
            if ("Inactive".equalsIgnoreCase(u.getStatus()) || u.getId().equals(unassigned) || u.getMergedIntoId() != null) continue;
            out.put(u.getId(), u);
        }
        return out;
    }

    /** The latest follow-up of each sponsor, with who made it. */
    private Map<Long, Map<String, Object>> lastFollowups() {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        List<SponsorFollowup> all = followups.findAllByOrderByIdDesc();
        Map<Long, String> who = new HashMap<>();
        users.findAllById(all.stream().map(SponsorFollowup::getCreatedBy).filter(Objects::nonNull).distinct().toList()).forEach(u -> who.put(u.getId(), u.getName()));
        for (SponsorFollowup f : all) {
            out.putIfAbsent(f.getSponsorUserId(), Map.of("at", f.getCreatedAt() == null ? "" : f.getCreatedAt().toString(), "channel", f.getChannel(),
                    "kind", f.getKind(), "by", who.getOrDefault(f.getCreatedBy(), ""), "note", f.getNote() == null ? "" : f.getNote()));
        }
        return out;
    }

    private static Map<String, Object> base(User u, Map<Long, Map<String, Object>> last) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sponsor_id", u.getId());
        r.put("name", u.getName());
        r.put("user_id", u.getUserId());
        r.put("chapter", u.getChapterName());
        r.put("has_email", usable(u.getEmail()));
        r.put("last_followup", last.get(u.getId()));
        return r;
    }

    static boolean usable(String email) {
        return email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    // ---------------------------------------------------------------- the two lists

    /** Sponsors with installments unpaid more than minDays after their due date, most overdue first. */
    public List<Map<String, Object>> lapsed(Integer minDays) {
        int days = minDays == null ? 30 : Math.max(0, minDays);
        LocalDate cutoff = LocalDate.now().minusDays(days);
        Map<Long, User> sponsors = sponsors();
        Map<Long, Map<String, Object>> last = lastFollowups();
        Map<Long, Map<String, Object>> byGrantor = new LinkedHashMap<>();
        for (PaymentInstallment i : installments.findAllByOrderByGranteeIdAscInstallmentNoAsc()) {
            if (i.getPaymentId() != null || i.getDueDate() == null || !i.getDueDate().isBefore(cutoff)) continue;
            User s = sponsors.get(i.getGrantorId());
            if (s == null) continue;
            Map<String, Object> r = byGrantor.computeIfAbsent(s.getId(), k -> {
                Map<String, Object> n = base(s, last);
                n.put("students", new HashSet<Long>());
                n.put("overdue_count", 0);
                n.put("oldest_due", i.getDueDate());
                return n;
            });
            @SuppressWarnings("unchecked") Set<Long> st = (Set<Long>) r.get("students");
            st.add(i.getGranteeId());
            r.put("overdue_count", (Integer) r.get("overdue_count") + 1);
            if (i.getDueDate().isBefore((LocalDate) r.get("oldest_due"))) r.put("oldest_due", i.getDueDate());
        }
        List<Map<String, Object>> rows = new ArrayList<>(byGrantor.values());
        rows.forEach(r -> r.put("students", ((Set<?>) r.get("students")).size()));
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDate) r.get("oldest_due")));
        return rows;
    }

    /** Active sponsors with no student now: since when, and who their last student was (or never had one). */
    public List<Map<String, Object>> available() {
        Map<Long, User> sponsors = sponsors();
        Map<Long, Map<String, Object>> last = lastFollowups();
        Set<Long> withStudent = new HashSet<>();
        mappings.findAll().forEach(m -> { if (m.getGrantorId() != null) withStudent.add(m.getGrantorId()); });
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User s : sponsors.values()) {
            if (withStudent.contains(s.getId())) continue;
            SponsorMappingHistory latest = history.findByGrantorIdOrderByStartedAtDesc(s.getId()).stream()
                    .filter(h -> h.getEndedAt() != null).max(Comparator.comparing(SponsorMappingHistory::getEndedAt)).orElse(null);
            Map<String, Object> r = base(s, last);
            r.put("since", latest == null ? null : latest.getEndedAt().toLocalDate());
            r.put("last_student", latest == null ? null : users.findById(latest.getGranteeId()).map(User::getName).orElse(null));
            r.put("never_sponsored", latest == null);
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Boolean) r.get("never_sponsored"))
                .thenComparing(r -> (LocalDate) r.get("since"), Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    // ---------------------------------------------------------------- follow-ups

    public List<Map<String, Object>> historyOf(Long sponsorId) {
        Map<Long, String> who = new HashMap<>();
        List<SponsorFollowup> list = followups.findBySponsorUserIdOrderByIdDesc(sponsorId);
        users.findAllById(list.stream().map(SponsorFollowup::getCreatedBy).filter(Objects::nonNull).distinct().toList()).forEach(u -> who.put(u.getId(), u.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SponsorFollowup f : list) {
            rows.add(Map.of("at", f.getCreatedAt() == null ? "" : f.getCreatedAt().toString(), "kind", f.getKind(), "channel", f.getChannel(),
                    "note", f.getNote() == null ? "" : f.getNote(), "by", who.getOrDefault(f.getCreatedBy(), "")));
        }
        return rows;
    }

    /** Body: { sponsorId, kind LAPSED | AVAILABLE, channel CALL | NOTE, note }. */
    @Transactional
    public SponsorFollowup record(Map<String, Object> body) {
        User s = sponsors().get(id(body.get("sponsorId")));
        if (s == null) throw bad("Choose an active sponsor.");
        SponsorFollowup f = new SponsorFollowup();
        f.setSponsorUserId(s.getId());
        f.setKind(code(body.get("kind"), KINDS, "LAPSED", "kind"));
        f.setChannel(code(body.get("channel"), Set.of("CALL", "NOTE"), "NOTE", "channel"));
        f.setNote(text(body.get("note"), "Note", 1000, true));
        return followups.save(f);
    }

    /**
     * Emails the chosen sponsors. Body: { sponsorIds: [...], kind LAPSED | AVAILABLE, message (optional personal
     * line), force (send even if contacted in the last 7 days) }. Returns how many were sent and skipped, and why.
     */
    @Transactional
    public Map<String, Object> email(Map<String, Object> body) {
        String kind = code(body.get("kind"), KINDS, null, "kind");
        if (kind == null) throw bad("Choose which email to send.");
        if (!(body.get("sponsorIds") instanceof List<?> ids) || ids.isEmpty()) throw bad("Choose at least one sponsor.");
        String message = text(body.get("message"), "Message", 1000, false);
        boolean force = flag(body.get("force"), false);
        Map<Long, User> sponsors = sponsors();
        Map<Long, Map<String, Object>> lapsed = new HashMap<>();
        if ("LAPSED".equals(kind)) lapsed(0).forEach(r -> lapsed.put((Long) r.get("sponsor_id"), r));
        Map<Long, Map<String, Object>> available = new HashMap<>();
        if ("AVAILABLE".equals(kind)) available().forEach(r -> available.put((Long) r.get("sponsor_id"), r));
        LocalDateTime quietSince = LocalDateTime.now().minusDays(QUIET_DAYS);
        int sent = 0;
        List<String> skipped = new ArrayList<>();
        for (Object o : ids) {
            User s = sponsors.get(id(o));
            if (s == null) continue;
            if (!usable(s.getEmail())) { skipped.add(s.getName() + ": no email address"); continue; }
            boolean recent = followups.findBySponsorUserIdOrderByIdDesc(s.getId()).stream()
                    .anyMatch(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(quietSince));
            if (recent && !force) { skipped.add(s.getName() + ": contacted in the last " + QUIET_DAYS + " days"); continue; }
            Map<String, Object> values = new HashMap<>();
            values.put("name", s.getName());
            values.put("message", message == null ? "" : message);
            EmailType type;
            if ("LAPSED".equals(kind)) {
                Map<String, Object> r = lapsed.get(s.getId());
                if (r == null) { skipped.add(s.getName() + ": nothing overdue"); continue; }
                type = EmailType.SPONSOR_FOLLOWUP_OVERDUE;
                values.put("overdue_count", r.get("overdue_count"));
                values.put("oldest_due", ((LocalDate) r.get("oldest_due")).format(DAY));
            } else {
                Map<String, Object> r = available.get(s.getId());
                if (r == null) { skipped.add(s.getName() + ": has a student now"); continue; }
                type = EmailType.SPONSOR_INVITE_ANOTHER;
                values.put("last_student", r.get("last_student") == null ? "students" : r.get("last_student"));
            }
            if (!email.send(type, s.getEmail().trim(), values)) { skipped.add(s.getName() + ": the email could not be sent (see Email Log)"); continue; }
            SponsorFollowup f = new SponsorFollowup();
            f.setSponsorUserId(s.getId());
            f.setKind(kind);
            f.setChannel("EMAIL");
            f.setNote(message == null ? ("LAPSED".equals(kind) ? "Reminder email" : "Invited to sponsor another student") : message);
            followups.save(f);
            sent++;
        }
        return Map.of("sent", sent, "skipped", skipped, "message", sent + " email(s) sent" + (skipped.isEmpty() ? "." : ", " + skipped.size() + " skipped."));
    }
}
