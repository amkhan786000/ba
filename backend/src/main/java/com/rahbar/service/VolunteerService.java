package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.entity.RccCenter;
import com.rahbar.entity.Role;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.VolunteerApplicationRepository;
import com.rahbar.security.Section;
import com.rahbar.util.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Volunteer sign-ups from the public website (mentor, tutor, sponsor, join a chapter), and their follow-up in
 * Admin > Volunteers. Staff with the Volunteers permission are notified of each new sign-up.
 */
@Service
public class VolunteerService {

    private static final Logger log = LoggerFactory.getLogger(VolunteerService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** More sign-ups than this from one email / phone within a day are refused. */
    static final int MAX_PER_DAY = 3;

    private final VolunteerApplicationRepository volunteers;
    private final ChapterRepository chapters;
    private final RccCenterRepository centers;
    private final RoleRepository roles;
    private final NotificationService notifications;
    private final com.rahbar.website.WebVolunteerOptionRepository options;

    public VolunteerService(VolunteerApplicationRepository volunteers, ChapterRepository chapters,
                            RccCenterRepository centers, RoleRepository roles, NotificationService notifications,
                            com.rahbar.website.WebVolunteerOptionRepository options) {
        this.options = options;
        this.volunteers = volunteers;
        this.chapters = chapters;
        this.centers = centers;
        this.roles = roles;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- public sign-up

    /**
     * Saves a sign-up. Body: fullName, email, phone (one of the two), city, country, homeDistrict, profession,
     * wantsMentor / wantsTutor / wantsSponsor / wantsChapter (at least one), mentorFields, tutorSubjects,
     * tutorGrades, tutorMode, rccCenterId, hoursPerMonth, sponsorStudents, chapterId, message, source, and
     * otherOffers: [{ key, choices: [...], extra: [...] }] for options of kind OTHER (Admin > Volunteers > Form options).
     * A filled-in "website" field (a trap for bots) is accepted but not saved.
     */
    @Transactional
    public void submit(Map<String, Object> body) {
        if (!ServiceSupport.isBlank(body.get("website"))) {
            log.info("Ignored a volunteer sign-up with the bot trap filled in");
            return;
        }
        VolunteerApplication v = new VolunteerApplication();
        v.setFullName(required(body, "fullName", "Your name", 150));
        v.setEmail(text(body, "email", "Email", 150));
        v.setPhone(text(body, "phone", "Phone", 30));
        if (v.getEmail() == null && v.getPhone() == null) throw bad("Please give an email address or a phone number.");
        if (v.getEmail() != null && !EMAIL.matcher(v.getEmail()).matches()) throw bad("Please check the email address.");
        v.setCity(text(body, "city", "City", 100));
        v.setCountry(text(body, "country", "Country", 100));
        v.setHomeDistrict(text(body, "homeDistrict", "Home district", 100));
        v.setProfession(text(body, "profession", "Profession", 150));

        v.setWantsMentor(flag(body, "wantsMentor"));
        v.setWantsTutor(flag(body, "wantsTutor"));
        v.setWantsSponsor(flag(body, "wantsSponsor"));
        v.setWantsChapter(flag(body, "wantsChapter"));
        v.setOtherOffers(otherOffers(body.get("otherOffers")));
        if (!v.isWantsMentor() && !v.isWantsTutor() && !v.isWantsSponsor() && !v.isWantsChapter() && v.getOtherOffers() == null) {
            throw bad("Please choose at least one way to help.");
        }
        if (v.isWantsMentor()) v.setMentorFields(text(body, "mentorFields", "Fields of expertise", 500));
        if (v.isWantsTutor()) {
            v.setTutorSubjects(text(body, "tutorSubjects", "Subjects", 500));
            v.setTutorGrades(text(body, "tutorGrades", "Grades", 200));
            String mode = text(body, "tutorMode", "Online / in person", 20);
            if (mode != null && !VolunteerApplication.TUTOR_MODES.containsKey(mode)) throw bad("Unknown tutoring mode.");
            v.setTutorMode(mode);
            Long centerId = number(body, "rccCenterId");
            if (centerId != null && !centers.existsById(centerId)) throw bad("Unknown RCC center.");
            v.setRccCenterId(centerId);
        }
        if (v.isWantsMentor() || v.isWantsTutor()) v.setHoursPerMonth(range(body, "hoursPerMonth", "Hours per month", 1, 200));
        if (v.isWantsSponsor()) v.setSponsorStudents(range(body, "sponsorStudents", "Number of students", 1, 100));
        if (v.isWantsChapter()) {
            Long chapterId = number(body, "chapterId");
            if (chapterId != null && chapters.findById(chapterId).filter(c -> !Boolean.FALSE.equals(c.getActive())).isEmpty()) {
                throw bad("Unknown chapter.");
            }
            v.setChapterId(chapterId);
        }
        v.setMessage(text(body, "message", "Message", 2000));
        v.setSource(text(body, "source", "Source", 50));

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long recent = volunteers.countByEmailIgnoreCaseAndCreatedAtAfterOrPhoneAndCreatedAtAfter(
                v.getEmail() == null ? "\u0000" : v.getEmail(), since, v.getPhone() == null ? "\u0000" : v.getPhone(), since);
        if (recent >= MAX_PER_DAY) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "We have already received your sign-up. We will be in touch soon.");
        }
        volunteers.save(v);
        notifyStaff(v);
    }

    /** In-app notification to everyone whose role may view volunteers (and the Super Admin). */
    private void notifyStaff(VolunteerApplication v) {
        List<Integer> roleIds = new ArrayList<>();
        for (Role r : roles.findAll()) {
            if (Integer.valueOf(Role.SUPER_ADMIN).equals(r.getRoleId()) || r.permissionSet().contains(Section.VOLUNTEERS.key(Section.Level.VIEW))) {
                roleIds.add(r.getRoleId());
            }
        }
        if (roleIds.isEmpty()) return;
        try {
            notifications.notifyRoles(roleIds, "New volunteer: " + v.getFullName(),
                    v.getFullName() + " offered to " + String.join(", ", offers(v)) + ".", "VOLUNTEER", "/admin/volunteers");
        } catch (RuntimeException e) {
            log.warn("Could not notify staff of volunteer {}: {}", v.getVolunteerId(), e.getMessage());
        }
    }

    static List<String> offers(VolunteerApplication v) {
        List<String> out = new ArrayList<>();
        if (v.isWantsMentor()) out.add("mentor");
        if (v.isWantsTutor()) out.add("tutor");
        if (v.isWantsSponsor()) out.add("sponsor students");
        if (v.isWantsChapter()) out.add("join a chapter");
        if (v.getOtherOffers() != null) {
            for (String line : v.getOtherOffers().split("\n")) out.add(line.split(":")[0].strip());
        }
        return out;
    }

    /**
     * The ticked options of kind OTHER as readable lines ("Title: a, b") with the option's English title at the time.
     * Unknown or hidden options are refused.
     */
    private String otherOffers(Object value) {
        if (!(value instanceof Collection<?> list) || list.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) throw bad("Invalid way to help.");
            String key = ServiceSupport.str(m.get("key"));
            com.rahbar.website.WebVolunteerOption o = (key == null ? Optional.<com.rahbar.website.WebVolunteerOption>empty() : options.findByOptionKey(key))
                    .filter(com.rahbar.website.WebVolunteerOption::isActive)
                    .filter(x -> x.getKind() == com.rahbar.website.WebVolunteerOption.Kind.OTHER)
                    .orElseThrow(() -> bad("This way to help is no longer offered. Please reload the page."));
            if (!seen.add(o.getOptionKey())) continue;
            List<String> picked = new ArrayList<>();
            picked.addAll(picked(m.get("choices")));
            picked.addAll(picked(m.get("extra")));
            lines.add(o.getTitle() + (picked.isEmpty() ? "" : ": " + String.join(", ", picked)));
        }
        String text = String.join("\n", lines);
        if (text.length() > 2000) throw bad("Too many choices.");
        return text.isEmpty() ? null : text;
    }

    /** The items picked, as the visitor saw them (they may be the Urdu / Hindi version of the list). */
    private static List<String> picked(Object sent) {
        if (!(sent instanceof Collection<?> items)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object i : items) {
            String s = i == null ? "" : String.valueOf(i).strip();
            if (!s.isEmpty() && s.length() <= 100 && !out.contains(s)) out.add(s.replace(",", " "));
        }
        return out.size() > 30 ? out.subList(0, 30) : out;
    }

    // ---------------------------------------------------------------- Admin > Volunteers

    /** Every sign-up, newest first, with chapter / center names, plus counts by status. */
    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        Map<Long, String> chapterNames = new HashMap<>();
        for (Chapter c : chapters.findAll()) chapterNames.put(c.getChapterId(), c.getChapterName());
        Map<Long, String> centerNames = new HashMap<>();
        for (RccCenter c : centers.findAll()) centerNames.put(c.getRccCenterId(), c.getCenterName());

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        VolunteerApplication.STATUSES.keySet().forEach(k -> byStatus.put(k, 0L));
        for (VolunteerApplication v : volunteers.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> row = Rows.of(v);
            row.put("chapter_name", chapterNames.get(v.getChapterId()));
            row.put("rcc_center_name", centerNames.get(v.getRccCenterId()));
            rows.add(row);
            byStatus.merge(v.getStatus(), 1L, Long::sum);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", rows);
        body.put("counts", byStatus);
        body.put("statuses", VolunteerApplication.STATUSES);
        body.put("tutorModes", VolunteerApplication.TUTOR_MODES);
        return body;
    }

    /** Body: { status, notes }. */
    @Transactional
    public void update(Long id, Map<String, Object> body) {
        VolunteerApplication v = find(id);
        if (body.containsKey("status")) {
            String status = ServiceSupport.str(body.get("status"));
            if (status == null || !VolunteerApplication.STATUSES.containsKey(status)) throw bad("Unknown status.");
            v.setStatus(status);
        }
        if (body.containsKey("notes")) v.setNotes(text(body, "notes", "Notes", 2000));
        volunteers.save(v);
    }

    @Transactional
    public void delete(Long id) {
        volunteers.delete(find(id));
    }

    private VolunteerApplication find(Long id) {
        return (id == null ? Optional.<VolunteerApplication>empty() : volunteers.findById(id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Volunteer not found."));
    }

    // ---------------------------------------------------------------- reading the form

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String text(Map<String, Object> body, String key, String label, int max) {
        Object v = body.get(key);
        if (v instanceof Collection<?> list) v = String.join(",", list.stream().map(String::valueOf).map(String::strip).toList());
        String s = ServiceSupport.isBlank(v) ? null : String.valueOf(v).strip();
        if (s != null && s.length() > max) throw bad(label + " is too long (at most " + max + " characters).");
        return s;
    }

    private static String required(Map<String, Object> body, String key, String label, int max) {
        String s = text(body, key, label, max);
        if (s == null) throw bad(label + " is required.");
        return s;
    }

    private static boolean flag(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static Long number(Map<String, Object> body, String key) {
        try {
            return ServiceSupport.toLong(body.get(key));
        } catch (NumberFormatException e) {
            throw bad("Invalid " + key + ".");
        }
    }

    private static Integer range(Map<String, Object> body, String key, String label, int min, int max) {
        Integer n;
        try {
            n = ServiceSupport.toInteger(body.get(key));
        } catch (NumberFormatException e) {
            throw bad(label + " must be a number.");
        }
        if (n != null && (n < min || n > max)) throw bad(label + " must be between " + min + " and " + max + ".");
        return n;
    }
}
