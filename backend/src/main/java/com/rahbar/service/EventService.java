package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import com.rahbar.util.UserRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.rahbar.service.FormInput.*;

/**
 * Community > Events: workshops, career sessions, parents' meetings, ceremonies... for an audience. Students and
 * sponsors sign up in their portal; staff add people (a user, a coaching student or a guest such as a parent) and
 * mark who came. A chapter lead runs their chapter's events, a coaching coordinator their center's; everyone with
 * Events sees the organisation-wide ones too.
 */
@Service
public class EventService {

    static final Set<String> KINDS = Set.of("WORKSHOP", "CAREER", "PARENTS", "EXAM_PREP", "CEREMONY", "OTHER");
    static final Set<String> AUDIENCES = Set.of("SCHOLARS", "COACHING", "SPONSORS", "PARENTS", "ALL");
    static final Set<String> STATUSES = Set.of("SCHEDULED", "CANCELLED", "DONE");

    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final UserRepository users;
    private final CoachingStudentRepository coachingStudents;
    private final ChapterRepository chapters;
    private final CoachingAccess coachingAccess;
    private final NotificationService notifications;

    public EventService(EventRepository events, EventRegistrationRepository registrations, UserRepository users,
                        CoachingStudentRepository coachingStudents, ChapterRepository chapters, CoachingAccess coachingAccess,
                        NotificationService notifications) {
        this.events = events;
        this.registrations = registrations;
        this.users = users;
        this.coachingStudents = coachingStudents;
        this.chapters = chapters;
        this.coachingAccess = coachingAccess;
        this.notifications = notifications;
    }

    // ================================================================ staff

    /** upcoming: true (today on), false (past) or null (all); each with how many registered and came. */
    public Map<String, Object> list(Boolean upcoming) {
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        Map<Long, long[]> counts = new HashMap<>();
        for (EventRegistration r : registrations.findAll()) {
            long[] c = counts.computeIfAbsent(r.getEventId(), k -> new long[2]);
            c[0]++;
            if (Boolean.TRUE.equals(r.getAttended())) c[1]++;
        }
        Map<Long, String> chapterNames = new HashMap<>();
        chapters.findAll().forEach(c -> chapterNames.put(c.getChapterId(), c.getChapterName()));
        Map<Long, String> centerNames = coachingAccess.names();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Event e : events.findAllByOrderByStartsAtDesc()) {
            if (!visible(e)) continue;
            boolean future = !e.getStartsAt().isBefore(today);
            if (upcoming != null && upcoming != future) continue;
            Map<String, Object> r = Rows.of(e);
            long[] c = counts.getOrDefault(e.getId(), new long[2]);
            r.put("registered", c[0]);
            r.put("attended", c[1]);
            r.put("chapter_name", e.getChapterId() == null ? null : chapterNames.get(e.getChapterId()));
            r.put("center_name", e.getRccCenterId() == null ? null : centerNames.get(e.getRccCenterId()));
            r.put("can_edit", mayEdit(e));
            rows.add(r);
        }
        if (Boolean.TRUE.equals(upcoming)) Collections.reverse(rows); // soonest first
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("scope", Access.chapterScoped() ? "CHAPTER" : Access.rccScoped() ? "RCC" : "ALL");
        out.put("chapters", Access.chapterScoped() || Access.rccScoped() ? List.of()
                : chapters.findAllByOrderByChapterNameAsc().stream().map(c -> Map.of("chapter_id", c.getChapterId(), "chapter_name", c.getChapterName())).toList());
        out.put("centers", coachingAccess.centers().stream().map(c -> Map.of("rcc_center_id", c.getRccCenterId(), "center_name", c.getCenterName())).toList());
        return out;
    }

    /**
     * Creates or changes an event. Chapter leads' events belong to their chapter, coaching coordinators' to their
     * center. notify: tell the audience in their portal (students / sponsors).
     */
    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> b) {
        Event e = id == null ? new Event() : requireEditable(id);
        boolean isNew = e.getId() == null;
        e.setTitle(text(b.get("title"), "Title", 200, true));
        e.setKind(code(b.get("kind"), KINDS, "WORKSHOP", "Kind"));
        e.setDescription(text(b.get("description"), "Description", 3000, false));
        e.setStartsAt(Optional.ofNullable(dateTime(b.get("startsAt"), "Starts")).orElseThrow(() -> bad("When does it start?")));
        e.setEndsAt(dateTime(b.get("endsAt"), "Ends"));
        if (e.getEndsAt() != null && e.getEndsAt().isBefore(e.getStartsAt())) throw bad("It can't end before it starts.");
        e.setVenue(text(b.get("venue"), "Venue", 300, false));
        e.setOnlineLink(text(b.get("onlineLink"), "Online link", 500, false));
        if (e.getOnlineLink() != null && !e.getOnlineLink().matches("(?i)https?://\\S+")) throw bad("The online link must start with http:// or https://");
        e.setAudience(code(b.get("audience"), AUDIENCES, "ALL", "Audience"));
        Integer capacity = ServiceSupport.isBlank(b.get("capacity")) ? null : ServiceSupport.toInteger(b.get("capacity"));
        if (capacity != null && capacity < 1) throw bad("Places must be at least 1, or empty for no limit.");
        e.setCapacity(capacity);
        e.setRegistrationOpen(!b.containsKey("registrationOpen") || flag(b.get("registrationOpen")));
        e.setStatus(code(b.get("status"), STATUSES, "SCHEDULED", "Status"));
        User me = Access.current().getUser();
        if (Access.chapterScoped()) {
            e.setChapterId(me.getChapterId());
            e.setRccCenterId(null);
        } else if (Access.rccScoped()) {
            e.setRccCenterId(me.getRccCenterId());
            e.setChapterId(null);
        } else {
            e.setChapterId(ServiceSupport.toLong(b.get("chapterId")));
            e.setRccCenterId(ServiceSupport.toLong(b.get("rccCenterId")));
        }
        events.save(e);
        if (isNew && flag(b.get("notify")) && "SCHEDULED".equals(e.getStatus())) notifyAudience(e);
        return Map.of("id", e.getId(), "message", "Event saved.");
    }

    @Transactional
    public void delete(Long id) {
        Event e = requireEditable(id);
        registrations.findByEventId(id).forEach(registrations::delete);
        events.delete(e);
    }

    /** Everyone registered, with who they are and whether they came. */
    public Map<String, Object> registrations(Long id) {
        Event e = requireVisible(id);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EventRegistration r : registrations.findByEventId(id)) {
            Map<String, Object> row = Rows.of(r);
            if (r.getUserId() != null) {
                User u = users.findById(r.getUserId()).orElse(null);
                row.put("name", u == null ? "?" : u.getName());
                row.put("who", u == null ? "" : who(u));
                row.put("phone", u == null ? null : u.getPhone());
            } else if (r.getCoachingStudentId() != null) {
                CoachingStudent s = coachingStudents.findById(r.getCoachingStudentId()).orElse(null);
                row.put("name", s == null ? "?" : s.getName());
                row.put("who", s == null ? "" : "Coaching class " + s.getClassLevel());
                row.put("phone", s == null ? null : s.contactMobile());
            } else {
                row.put("name", r.getGuestName());
                row.put("who", "Guest");
                row.put("phone", r.getGuestPhone());
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("event", Rows.of(e));
        out.put("can_edit", mayEdit(e));
        out.put("rows", rows);
        return out;
    }

    /** Staff add someone: { userId } or { coachingStudentId } or { guestName, guestPhone }. */
    @Transactional
    public Map<String, Object> addRegistration(Long id, Map<String, Object> b) {
        Event e = requireEditable(id);
        EventRegistration r = new EventRegistration();
        r.setEventId(id);
        Long userId = ServiceSupport.toLong(b.get("userId"));
        Long coachingId = ServiceSupport.toLong(b.get("coachingStudentId"));
        if (userId != null) {
            users.findById(userId).orElseThrow(() -> notFound("Person"));
            if (registrations.findByEventIdAndUserId(id, userId).isPresent()) throw bad("Already registered.");
            r.setUserId(userId);
        } else if (coachingId != null) {
            CoachingStudent s = coachingStudents.findById(coachingId).orElseThrow(() -> notFound("Student"));
            coachingAccess.requireVisible(s.getRccCenterId(), "Student");
            if (registrations.findByEventId(id).stream().anyMatch(x -> coachingId.equals(x.getCoachingStudentId()))) throw bad("Already registered.");
            r.setCoachingStudentId(coachingId);
        } else {
            r.setGuestName(text(b.get("guestName"), "Name", 150, true));
            r.setGuestPhone(text(b.get("guestPhone"), "Phone", 20, false));
        }
        requireRoom(e);
        registrations.save(r);
        return Map.of("message", "Added.");
    }

    @Transactional
    public void removeRegistration(Long id, Long registrationId) {
        requireEditable(id);
        EventRegistration r = registrations.findById(registrationId).filter(x -> x.getEventId().equals(id)).orElseThrow(() -> notFound("Registration"));
        registrations.delete(r);
    }

    /** { attended: { registrationId: true | false | null } }. */
    @Transactional
    public Map<String, Object> markAttendance(Long id, Map<String, Object> b) {
        requireEditable(id);
        int n = 0;
        if (b.get("attended") instanceof Map<?, ?> m) {
            for (EventRegistration r : registrations.findByEventId(id)) {
                String key = String.valueOf(r.getId());
                if (!m.containsKey(key)) continue;
                Object v = m.get(key);
                r.setAttended(v == null ? null : flag(v));
                registrations.save(r);
                n++;
            }
        }
        return Map.of("message", n + " marked.");
    }

    /** People staff can add: students and sponsors (users), and coaching students of their centers, matching q. */
    public List<Map<String, Object>> people(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        users.findAll().stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null)
                .filter(u -> com.rahbar.util.Paging.matches(q, u.getName(), u.getUserId(), u.getPhone()))
                .limit(15).forEach(u -> out.add(Map.of("user_id", u.getId(), "name", u.getName(), "who", who(u))));
        Set<Long> centers = coachingAccess.centerIds();
        coachingStudents.findAll().stream()
                .filter(s -> centers.contains(s.getRccCenterId()) && "ACTIVE".equals(s.getStatus()))
                .filter(s -> com.rahbar.util.Paging.matches(q, s.getName(), s.getAdmissionNo(), s.contactMobile()))
                .limit(15).forEach(s -> out.add(Map.of("coaching_student_id", s.getId(), "name", s.getName(), "who", "Coaching class " + s.getClassLevel())));
        return out;
    }

    // ================================================================ portal (students and sponsors)

    /** Upcoming events for me (by audience and my chapter), with whether I'm registered and places left. */
    public List<Map<String, Object>> forMe(User me) {
        Set<String> audiences = audiencesOf(me);
        LocalDateTime now = LocalDateTime.now().toLocalDate().atStartOfDay();
        Map<Long, Long> taken = new HashMap<>();
        registrations.findAll().forEach(r -> taken.merge(r.getEventId(), 1L, Long::sum));
        Set<Long> mine = new HashSet<>();
        registrations.findByUserId(me.getId()).forEach(r -> mine.add(r.getEventId()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Event e : events.findAllByOrderByStartsAtDesc()) {
            if (e.getStartsAt().isBefore(now) || "CANCELLED".equals(e.getStatus()) && !mine.contains(e.getId())) continue;
            if (!audiences.contains(e.getAudience()) || e.getRccCenterId() != null) continue;
            if (e.getChapterId() != null && !e.getChapterId().equals(me.getChapterId())) continue;
            Map<String, Object> r = Rows.pick(e, "id", "title", "kind", "description", "starts_at", "ends_at", "venue", "online_link", "status", "registration_open");
            r.put("registered", mine.contains(e.getId()));
            r.put("places_left", e.getCapacity() == null ? null : Math.max(0, e.getCapacity() - taken.getOrDefault(e.getId(), 0L)));
            rows.add(r);
        }
        Collections.reverse(rows);
        return rows;
    }

    @Transactional
    public Map<String, Object> register(User me, Long eventId, boolean on) {
        Event e = events.findById(eventId).orElseThrow(() -> notFound("Event"));
        if (forMe(me).stream().noneMatch(r -> eventId.equals(r.get("id")))) throw notFound("Event");
        Optional<EventRegistration> existing = registrations.findByEventIdAndUserId(eventId, me.getId());
        if (!on) {
            existing.ifPresent(registrations::delete);
            return Map.of("message", "You're no longer registered.");
        }
        if (existing.isPresent()) return Map.of("message", "You're already registered.");
        if (!e.isRegistrationOpen() || !"SCHEDULED".equals(e.getStatus())) throw bad("Registration for this event is closed.");
        requireRoom(e);
        EventRegistration r = new EventRegistration();
        r.setEventId(eventId);
        r.setUserId(me.getId());
        r.setSelfRegistered(true);
        registrations.save(r);
        return Map.of("message", "You're registered. See you there!");
    }

    // ================================================================ helpers

    /** Students see student and parents' events (their family comes too); sponsors see sponsors' events; all see ALL. */
    private Set<String> audiencesOf(User me) {
        Set<String> out = new HashSet<>(Set.of("ALL"));
        if (UserRoles.has(me, ServiceSupport.STUDENT_ROLE, users)) { out.add("SCHOLARS"); out.add("PARENTS"); }
        if (UserRoles.has(me, 5, users)) out.add("SPONSORS");
        return out;
    }

    private void notifyAudience(Event e) {
        List<Integer> roles = switch (e.getAudience()) {
            case "SCHOLARS", "PARENTS" -> List.of(ServiceSupport.STUDENT_ROLE);
            case "SPONSORS" -> List.of(5);
            case "ALL" -> List.of(ServiceSupport.STUDENT_ROLE, 5);
            default -> List.of();
        };
        if (roles.isEmpty()) return;
        String when = e.getStartsAt().toLocalDate() + " " + e.getStartsAt().toLocalTime();
        for (Integer role : roles) {
            for (User u : users.findByRoleId(role)) {
                if ("Inactive".equalsIgnoreCase(u.getStatus()) || u.getMergedIntoId() != null) continue;
                if (e.getChapterId() != null && !e.getChapterId().equals(u.getChapterId())) continue;
                notifications.notify(u.getId(), "New event: " + e.getTitle(), "On " + when + (e.getVenue() == null ? "" : " at " + e.getVenue())
                        + ". Register on your Events page.", "events", role == 5 ? "/sponsor/events" : "/student/events", false);
            }
        }
    }

    private void requireRoom(Event e) {
        if (e.getCapacity() != null && registrations.findByEventId(e.getId()).size() >= e.getCapacity()) throw bad("The event is full.");
    }

    /** Office: everything; chapter lead: org-wide + their chapter's; coaching coordinator / teacher: org-wide + their center's. */
    boolean visible(Event e) {
        if (!Access.chapterScoped() && !Access.rccScoped()) return true;
        User me = Access.current().getUser();
        if (e.getChapterId() == null && e.getRccCenterId() == null) return true;
        return Access.chapterScoped() ? Objects.equals(e.getChapterId(), me.getChapterId()) : Objects.equals(e.getRccCenterId(), me.getRccCenterId());
    }

    /** Office edits all; a chapter lead / coordinator only their own chapter's / center's events. */
    boolean mayEdit(Event e) {
        if (!Access.can(com.rahbar.security.Section.EVENTS, com.rahbar.security.Section.Level.EDIT)) return false;
        if (!Access.chapterScoped() && !Access.rccScoped()) return true;
        User me = Access.current().getUser();
        return Access.chapterScoped() ? e.getChapterId() != null && e.getChapterId().equals(me.getChapterId())
                : e.getRccCenterId() != null && e.getRccCenterId().equals(me.getRccCenterId());
    }

    private Event requireVisible(Long id) {
        Event e = events.findById(id).orElseThrow(() -> notFound("Event"));
        if (!visible(e)) throw notFound("Event");
        return e;
    }

    private Event requireEditable(Long id) {
        Event e = requireVisible(id);
        if (!mayEdit(e)) throw Access.forbidden("Only the office, or the chapter / center that runs it, can change this event.");
        return e;
    }

    private String who(User u) {
        if (UserRoles.has(u, ServiceSupport.STUDENT_ROLE, users)) return "Student" + (u.getUserId() == null ? "" : " " + u.getUserId());
        if (UserRoles.has(u, 5, users)) return "Sponsor";
        return "Staff";
    }
}
