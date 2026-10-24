package com.rahbar.service;

import com.rahbar.entity.HelplineMessage;
import com.rahbar.entity.HelplineTicket;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.HelplineMessageRepository;
import com.rahbar.repository.HelplineTicketRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import com.rahbar.util.UserRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.FormInput.*;

/**
 * Helpline: students and sponsors raise an issue in their portal; the office (Helpline permission) assigns, replies
 * and resolves it. Internal notes stay with staff. A confidential issue (and every harassment report) is seen only by
 * the Super Admin and whoever it is assigned to.
 */
@Service
public class HelplineService {

    public static final String STUDENT = "STUDENT", SPONSOR = "SPONSOR";
    static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("FEES", "Fees / installments");
        CATEGORIES.put("PAYMENT", "A payment");
        CATEGORIES.put("COLLEGE", "College / institute");
        CATEGORIES.put("STUDIES", "Studies");
        CATEGORIES.put("STUDENT", "My sponsored student");
        CATEGORIES.put("HEALTH", "Health");
        CATEGORIES.put("HARASSMENT", "Harassment / safety");
        CATEGORIES.put("ACCOUNT", "My account");
        CATEGORIES.put("PERSONAL", "Personal / family");
        CATEGORIES.put("OTHER", "Other");
    }
    static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    static final Set<String> PRIORITIES = Set.of("NORMAL", "URGENT");

    private final HelplineTicketRepository tickets;
    private final HelplineMessageRepository messages;
    private final UserRepository users;
    private final RoleRepository roles;
    private final NotificationService notifications;

    public HelplineService(HelplineTicketRepository tickets, HelplineMessageRepository messages, UserRepository users, RoleRepository roles,
                           NotificationService notifications) {
        this.tickets = tickets;
        this.messages = messages;
        this.users = users;
        this.roles = roles;
        this.notifications = notifications;
    }

    // ================================================================ students and sponsors

    /** My issues, newest first, and the categories I can choose. */
    public Map<String, Object> mine(User me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", tickets.findByRaisedByOrderByIdDesc(me.getId()).stream().map(t -> row(t, null)).toList());
        out.put("categories", categoriesFor(kindOf(me)));
        return out;
    }

    /** { category, subject, message, confidential }. */
    @Transactional
    public Map<String, Object> raise(User me, Map<String, Object> b) {
        String kind = kindOf(me);
        HelplineTicket t = new HelplineTicket();
        t.setRaisedBy(me.getId());
        t.setRaiserKind(kind);
        t.setCategory(code(b.get("category"), categoriesFor(kind).keySet(), "OTHER", "What it's about"));
        t.setSubject(text(b.get("subject"), "Subject", 200, true));
        String body = text(b.get("message"), "Message", 4000, true);
        boolean harassment = "HARASSMENT".equals(t.getCategory());
        t.setConfidential(harassment || flag(b.get("confidential")));
        t.setPriority(harassment ? "URGENT" : "NORMAL");
        t.setLastActivityAt(LocalDateTime.now());
        tickets.save(t);
        message(t, me.getId(), body, false);
        String title = (t.isConfidential() ? "Confidential helpline issue" : "New helpline issue") + " #" + t.getId();
        if (t.isConfidential()) {
            notifications.notifyRoles(List.of(Role.SUPER_ADMIN), title, "A " + kind.toLowerCase(Locale.ROOT) + " raised a confidential issue.", "helpline", "/admin/helpline");
        } else {
            for (Long staff : handlers()) notifications.notify(staff, title, me.getName() + ": " + t.getSubject(), "helpline", "/admin/helpline", false);
        }
        return Map.of("id", t.getId(), "message", "Thank you. Your issue #" + t.getId() + " has reached the office; you'll get a reply here.");
    }

    /** One of my issues with the conversation (no internal notes). */
    public Map<String, Object> mineDetail(User me, Long id) {
        HelplineTicket t = tickets.findById(id).filter(x -> x.getRaisedBy().equals(me.getId())).orElseThrow(() -> notFound("Issue"));
        return detail(t, false);
    }

    @Transactional
    public Map<String, Object> replyMine(User me, Long id, Map<String, Object> b) {
        HelplineTicket t = tickets.findById(id).filter(x -> x.getRaisedBy().equals(me.getId())).orElseThrow(() -> notFound("Issue"));
        if ("CLOSED".equals(t.getStatus())) throw bad("This issue is closed. Raise a new one if you need more help.");
        if (flag(b.get("solved"))) {
            t.setStatus("RESOLVED");
            t.setResolvedAt(LocalDateTime.now());
        } else {
            message(t, me.getId(), text(b.get("message"), "Message", 4000, true), false);
            if ("RESOLVED".equals(t.getStatus())) t.setStatus("OPEN"); // writing again reopens it
        }
        t.setLastActivityAt(LocalDateTime.now());
        tickets.save(t);
        List<Long> tell = t.getAssignedTo() != null ? List.of(t.getAssignedTo()) : t.isConfidential() ? superAdmins() : handlers();
        for (Long staff : tell) notifications.notify(staff, "Helpline #" + t.getId() + ": " + (flag(b.get("solved")) ? "marked solved" : "new reply"),
                me.getName() + ": " + t.getSubject(), "helpline", "/admin/helpline", false);
        return Map.of("message", flag(b.get("solved")) ? "Glad it's sorted!" : "Sent.");
    }

    // ================================================================ office

    /** Issues the user may see (confidential: Super Admin and the assignee), filtered; open and urgent first. */
    public Map<String, Object> list(String status, String category, Boolean mineOnly, String q) {
        Long me = Access.current().getUser().getId();
        Map<Long, User> people = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        STATUSES.forEach(s -> counts.put(s, 0L));
        for (HelplineTicket t : tickets.findAllByOrderByIdDesc()) {
            if (!maySee(t, me)) continue;
            counts.merge(t.getStatus(), 1L, Long::sum);
            if (status != null && !status.isBlank() && !"ACTIVE".equals(status) && !status.equals(t.getStatus())) continue;
            if ("ACTIVE".equals(status) && ("RESOLVED".equals(t.getStatus()) || "CLOSED".equals(t.getStatus()))) continue;
            if (category != null && !category.isBlank() && !category.equals(t.getCategory())) continue;
            if (Boolean.TRUE.equals(mineOnly) && !me.equals(t.getAssignedTo())) continue;
            User raiser = people.computeIfAbsent(t.getRaisedBy(), k -> users.findById(k).orElse(null));
            if (!com.rahbar.util.Paging.matches(q, t.getSubject(), "#" + t.getId(), raiser == null ? null : raiser.getName(),
                    raiser == null ? null : raiser.getUserId())) continue;
            rows.add(row(t, people));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> "RESOLVED".equals(r.get("status")) || "CLOSED".equals(r.get("status")))
                .thenComparing(r -> !"URGENT".equals(r.get("priority"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("counts", counts);
        out.put("categories", CATEGORIES);
        out.put("staff", staff());
        return out;
    }

    public Map<String, Object> staffDetail(Long id) {
        return detail(requireVisible(id), true);
    }

    /** Staff reply ({ message, internal }) and / or change { status, priority, assignedTo, confidential }. */
    @Transactional
    public Map<String, Object> staffUpdate(Long id, Map<String, Object> b) {
        HelplineTicket t = requireVisible(id);
        Long me = Access.current().getUser().getId();
        String reply = text(b.get("message"), "Message", 4000, false);
        boolean internal = flag(b.get("internal"));
        if (reply != null) message(t, me, reply, internal);
        if (b.containsKey("status")) {
            String s = code(b.get("status"), STATUSES, t.getStatus(), "Status");
            if (("RESOLVED".equals(s) || "CLOSED".equals(s)) && t.getResolvedAt() == null) t.setResolvedAt(LocalDateTime.now());
            if ("OPEN".equals(s) || "IN_PROGRESS".equals(s)) t.setResolvedAt(null);
            t.setStatus(s);
        } else if (reply != null && !internal && "OPEN".equals(t.getStatus())) {
            t.setStatus("IN_PROGRESS");
        }
        if (b.containsKey("priority")) t.setPriority(code(b.get("priority"), PRIORITIES, t.getPriority(), "Priority"));
        if (b.containsKey("assignedTo")) {
            Long to = ServiceSupport.toLong(b.get("assignedTo"));
            if (to != null && staff().stream().noneMatch(s -> to.equals(s.get("id")))) throw bad("Choose someone who handles the helpline.");
            if (to != null && !to.equals(t.getAssignedTo()) && !to.equals(me)) {
                notifications.notify(to, "Helpline #" + t.getId() + " assigned to you", t.getSubject(), "helpline", "/admin/helpline", false);
            }
            t.setAssignedTo(to);
        }
        if (b.containsKey("confidential") && Access.isSuperAdmin()) t.setConfidential(flag(b.get("confidential")));
        t.setLastActivityAt(LocalDateTime.now());
        tickets.save(t);
        if (reply != null && !internal) {
            notifications.notify(t.getRaisedBy(), "Reply to your issue #" + t.getId(), t.getSubject(), "helpline",
                    SPONSOR.equals(t.getRaiserKind()) ? "/sponsor/helpline" : "/student/helpline", true);
        }
        return Map.of("message", "Saved.");
    }

    // ================================================================ helpers

    private Map<String, Object> detail(HelplineTicket t, boolean staffView) {
        Map<Long, User> people = new HashMap<>();
        Map<String, Object> out = row(t, people);
        List<Map<String, Object>> conv = new ArrayList<>();
        for (HelplineMessage m : messages.findByTicketIdOrderByIdAsc(t.getId())) {
            if (m.isInternal() && !staffView) continue;
            User a = people.computeIfAbsent(m.getAuthorId(), k -> users.findById(k).orElse(null));
            Map<String, Object> r = Rows.of(m);
            boolean fromRaiser = m.getAuthorId().equals(t.getRaisedBy());
            r.put("from_raiser", fromRaiser);
            // the raiser sees "Rahbar office", not which staff member answered
            r.put("author", fromRaiser ? (a == null ? "?" : a.getName()) : staffView ? (a == null ? "Office" : a.getName()) : "Rahbar office");
            conv.add(r);
        }
        out.put("messages", conv);
        return out;
    }

    private Map<String, Object> row(HelplineTicket t, Map<Long, User> people) {
        Map<String, Object> r = Rows.of(t);
        r.put("category_label", CATEGORIES.getOrDefault(t.getCategory(), t.getCategory()));
        if (people != null) {
            User raiser = people.computeIfAbsent(t.getRaisedBy(), k -> users.findById(k).orElse(null));
            r.put("raiser_name", raiser == null ? "?" : raiser.getName());
            r.put("raiser_code", raiser == null ? null : raiser.getUserId());
            r.put("raiser_phone", raiser == null ? null : raiser.getPhone());
            r.put("raiser_chapter", raiser == null ? null : raiser.getChapterName());
            User a = t.getAssignedTo() == null ? null : people.computeIfAbsent(t.getAssignedTo(), k -> users.findById(k).orElse(null));
            r.put("assigned_name", a == null ? null : a.getName());
        }
        return r;
    }

    private void message(HelplineTicket t, Long author, String body, boolean internal) {
        HelplineMessage m = new HelplineMessage();
        m.setTicketId(t.getId());
        m.setAuthorId(author);
        m.setBody(body);
        m.setInternal(internal);
        m.setCreatedAt(LocalDateTime.now());
        messages.save(m);
    }

    /** Confidential issues: the Super Admin and the assignee only. */
    boolean maySee(HelplineTicket t, Long me) {
        return !t.isConfidential() || Access.isSuperAdmin() || me.equals(t.getAssignedTo());
    }

    private HelplineTicket requireVisible(Long id) {
        HelplineTicket t = tickets.findById(id).orElseThrow(() -> notFound("Issue"));
        if (!maySee(t, Access.current().getUser().getId())) throw notFound("Issue");
        return t;
    }

    /** A student (has the student role) or a sponsor. */
    String kindOf(User me) {
        if (UserRoles.has(me, ServiceSupport.STUDENT_ROLE, users)) return STUDENT;
        if (UserRoles.has(me, 5, users)) return SPONSOR;
        throw Access.forbidden("The helpline is for students and sponsors.");
    }

    static Map<String, String> categoriesFor(String kind) {
        List<String> keys = STUDENT.equals(kind) ? List.of("FEES", "COLLEGE", "STUDIES", "HEALTH", "HARASSMENT", "PERSONAL", "ACCOUNT", "OTHER")
                : List.of("PAYMENT", "STUDENT", "ACCOUNT", "OTHER");
        Map<String, String> out = new LinkedHashMap<>();
        keys.forEach(k -> out.put(k, CATEGORIES.get(k)));
        return out;
    }

    /** Active staff whose role may handle the helpline (Helpline: manage), plus Super Admins. */
    List<Map<String, Object>> staff() {
        Set<Integer> handlerRoles = new HashSet<>();
        handlerRoles.add(Role.SUPER_ADMIN);
        for (Role r : roles.findAll()) if (r.permissionSet().contains("HELPLINE:EDIT")) handlerRoles.add(r.getRoleId());
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Integer role : handlerRoles) {
            for (User u : users.findByRoleId(role)) {
                if ("Inactive".equalsIgnoreCase(u.getStatus()) || !seen.add(u.getId())) continue;
                out.add(Map.of("id", u.getId(), "name", u.getName()));
            }
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private List<Long> handlers() {
        return staff().stream().map(m -> (Long) m.get("id")).toList();
    }

    private List<Long> superAdmins() {
        return users.findByRoleId(Role.SUPER_ADMIN).stream().filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus())).map(User::getId).toList();
    }
}
