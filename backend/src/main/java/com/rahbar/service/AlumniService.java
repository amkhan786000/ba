package com.rahbar.service;

import com.rahbar.entity.AlumniProfile;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.AlumniProfileRepository;
import com.rahbar.repository.StudentInstitutionCourseRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Alumni: students whose study status is Graduated, and what they do now (job, higher studies, ...).
 * The office keeps the list (Admin > Alumni); graduates update their own profile from their portal.
 */
@Service
public class AlumniService {

    private final AlumniProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;

    public AlumniService(AlumniProfileRepository profileRepository, UserRepository userRepository,
                         StudentInstitutionCourseRepository studentCourseRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.studentCourseRepository = studentCourseRepository;
    }

    /** Every graduate (chapter-scoped users: their chapter's), with their profile, and counts by current status. */
    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        Long chapterId = Access.chapterScoped() ? Access.current().getUser().getChapterId() : null;
        if (Access.chapterScoped() && chapterId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "You are not linked to a chapter.");
        if (Access.rccScoped()) throw Access.forbidden("Alumni are not available for RCC-scoped roles.");
        Map<Long, AlumniProfile> profiles = new HashMap<>();
        profileRepository.findAll().forEach(p -> profiles.put(p.getUserId(), p));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
            boolean graduated = StudyStatus.GRADUATED.equals(u.getStudyStatus());
            if (!graduated && !profiles.containsKey(u.getId())) continue;
            if (u.getMergedIntoId() != null) continue;
            if (chapterId != null && !chapterId.equals(u.getChapterId())) continue;
            rows.add(row(u, profiles.get(u.getId())));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        AlumniProfile.STATUSES.forEach((code, label) -> byStatus.put(code, rows.stream().filter(r -> code.equals(r.get("current_status"))).count()));
        byStatus.put("UNKNOWN", rows.stream().filter(r -> r.get("current_status") == null).count());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rows", rows);
        body.put("byStatus", byStatus);
        body.put("statuses", AlumniProfile.STATUSES);
        body.put("consenting", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("consent_to_contact"))).count());
        return body;
    }

    /** Staff: saves a graduate's profile. */
    @Transactional
    public Map<String, Object> save(Long userId, Map<String, Object> body) {
        User u = requireStudent(userId);
        if (Access.chapterScoped() && !Objects.equals(Access.current().getUser().getChapterId(), u.getChapterId())) {
            throw Access.forbidden("You can only edit your own chapter's alumni.");
        }
        return row(u, apply(u, body));
    }

    /** A graduate's own profile. */
    @Transactional(readOnly = true)
    public Map<String, Object> mine(Long userId) {
        User u = requireStudent(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("graduated", StudyStatus.GRADUATED.equals(u.getStudyStatus()));
        body.put("profile", row(u, profileRepository.findById(userId).orElse(null)));
        body.put("statuses", AlumniProfile.STATUSES);
        return body;
    }

    /** A graduate updates their own profile (only once their study status is Graduated). */
    @Transactional
    public Map<String, Object> saveMine(Long userId, Map<String, Object> body) {
        User u = requireStudent(userId);
        if (!StudyStatus.GRADUATED.equals(u.getStudyStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your alumni profile opens once the office marks you as graduated.");
        }
        apply(u, body);
        return mine(userId);
    }

    private AlumniProfile apply(User u, Map<String, Object> b) {
        AlumniProfile p = profileRepository.findById(u.getId()).orElseGet(() -> {
            AlumniProfile n = new AlumniProfile();
            n.setUserId(u.getId());
            return n;
        });
        String status = text(b.get("currentStatus"), 30);
        if (status != null && !AlumniProfile.STATUSES.containsKey(status)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown status.");
        p.setCurrentStatus(status);
        p.setOrganisation(text(b.get("organisation"), 200));
        p.setRoleTitle(text(b.get("roleTitle"), 200));
        p.setCity(text(b.get("city"), 100));
        p.setCountry(text(b.get("country"), 100));
        String link = text(b.get("linkedinUrl"), 300);
        if (link != null && !link.matches("(?i)https?://.+")) throw new ApiException(HttpStatus.BAD_REQUEST, "The LinkedIn link must start with https://");
        p.setLinkedinUrl(link);
        Object year = b.get("graduationYear");
        Integer y = year == null || String.valueOf(year).isBlank() ? null : parseYear(year);
        p.setGraduationYear(y);
        p.setConsentToContact(Boolean.parseBoolean(String.valueOf(b.get("consentToContact"))));
        p.setNotes(text(b.get("notes"), 2000));
        return profileRepository.save(p);
    }

    private Map<String, Object> row(User u, AlumniProfile p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId());
        row.put("user_id", u.getUserId());
        row.put("name", u.getName());
        row.put("email", u.getEmail());
        row.put("phone", u.getPhone());
        row.put("chapter", u.getChapterName());
        row.put("study_status", StudyStatus.of(u.getStudyStatus()));
        row.put("graduated_on", u.getStudyStatusDate());
        Map<String, Object> course = Rows.first(studentCourseRepository.findCourseDetails(u.getId()));
        row.put("course", course == null ? null : course.get("course_name"));
        row.put("institution", course == null ? null : course.get("institution_name"));
        row.put("current_status", p == null ? null : p.getCurrentStatus());
        row.put("current_status_label", p == null || p.getCurrentStatus() == null ? null : AlumniProfile.STATUSES.get(p.getCurrentStatus()));
        row.put("organisation", p == null ? null : p.getOrganisation());
        row.put("role_title", p == null ? null : p.getRoleTitle());
        row.put("city", p == null ? null : p.getCity());
        row.put("country", p == null ? null : p.getCountry());
        row.put("linkedin_url", p == null ? null : p.getLinkedinUrl());
        Integer year = p == null ? null : p.getGraduationYear();
        if (year == null && u.getStudyStatusDate() != null && StudyStatus.GRADUATED.equals(u.getStudyStatus())) year = u.getStudyStatusDate().getYear();
        row.put("graduation_year", year);
        row.put("consent_to_contact", p != null && Boolean.TRUE.equals(p.getConsentToContact()));
        row.put("notes", p == null ? null : p.getNotes());
        row.put("updated_at", p == null ? null : p.getUpdatedAt());
        return row;
    }

    private User requireStudent(Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found."));
        if (!Integer.valueOf(ServiceSupport.STUDENT_ROLE).equals(u.getRoleId())) throw new ApiException(HttpStatus.BAD_REQUEST, "Only students have an alumni profile.");
        return u;
    }

    private static String text(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Integer parseYear(Object v) {
        try {
            int y = Integer.parseInt(String.valueOf(v).trim());
            if (y < 1980 || y > 2100) throw new NumberFormatException();
            return y;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Graduation year must be a year like 2026.");
        }
    }
}
