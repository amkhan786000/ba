package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mentoring: reading fields out of course names and sign-ups, ranking mentors, and who may be paired. */
class MentoringServiceTest {

    private final MentorshipRepository pairs = mock(MentorshipRepository.class);
    private final VolunteerApplicationRepository volunteers = mock(VolunteerApplicationRepository.class);
    private final AlumniProfileRepository profiles = mock(AlumniProfileRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentInstitutionCourseRepository courses = mock(StudentInstitutionCourseRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final MentoringService service = new MentoringService(pairs, volunteers, profiles, users, courses, scope);

    private final User student = user(1L, "Sana", null, 3L);
    private final User graduate = user(2L, "Imran", StudyStatus.GRADUATED, 3L);

    private static User user(Long id, String name, String status, Long chapter) {
        User u = new User();
        u.setId(id);
        u.setUserId("S" + id);
        u.setName(name);
        u.setRoleId(ServiceSupport.STUDENT_ROLE);
        u.setStudyStatus(status);
        u.setChapterId(chapter);
        return u;
    }

    private static VolunteerApplication volunteer(long id, String name, boolean mentor, boolean tutor, String fields, String status) {
        VolunteerApplication v = new VolunteerApplication();
        v.setVolunteerId(id);
        v.setFullName(name);
        v.setWantsMentor(mentor);
        v.setWantsTutor(tutor);
        v.setMentorFields(fields);
        v.setStatus(status);
        return v;
    }

    private static Map<String, Object> course(String name, String institution) {
        Map<String, Object> c = new HashMap<>();
        c.put("course_name", name);
        c.put("institution_name", institution);
        return c;
    }

    @BeforeEach
    void setUp() {
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        when(scope.requireStudent(1L)).thenReturn(student);
        when(scope.requireStudent(2L)).thenReturn(graduate);
        when(users.findByRoleId(ServiceSupport.STUDENT_ROLE)).thenReturn(List.of(student, graduate));
        when(courses.findCourseDetails(1L)).thenReturn(List.of(course("B.Tech (Computer Science)", "NIT Patna")));
        when(courses.findCourseDetails(2L)).thenReturn(List.of(course("MBBS", "Patna Medical College")));
        when(pairs.findByStatus(MentoringService.ACTIVE)).thenReturn(List.of());
        when(pairs.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void fieldsAreReadFromCourseNamesAndFreeText() {
        assertEquals(Set.of("engineering", "computing"), MentoringService.fields("B.Tech (Computer Science)", "NIT Patna"));
        assertEquals(Set.of("medicine"), MentoringService.fields("MBBS", "Patna Medical College"));
        assertEquals(Set.of("computing", "mathematics"), MentoringService.fields("Software developer, can teach Maths"));
        assertEquals(Set.of("civil-services"), MentoringService.fields("Civil services (UPSC) guidance"));
        assertTrue(MentoringService.fields(null, "").isEmpty());
    }

    @Test
    void suggestionsPutSharedFieldsFirstAndSkipDeclinedAndCurrentMentors() {
        AlumniProfile p = new AlumniProfile();
        p.setUserId(2L);
        p.setConsentToContact(true);
        when(profiles.findAll()).thenReturn(List.of(p));
        when(volunteers.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                volunteer(10, "Asif", true, false, "Software engineering, careers in IT companies", "NEW"),
                volunteer(11, "Bushra", true, false, "Banking and finance", "ONBOARDED"),
                volunteer(12, "Declined Dan", true, true, "Computer science", "DECLINED"),
                volunteer(13, "Chapter only", false, false, null, "NEW"),
                volunteer(14, "Already paired", true, false, "Computer science", "NEW")));
        Mentorship current = new Mentorship();
        current.setStudentUserId(1L);
        current.setVolunteerId(14L);
        current.setStatus(MentoringService.ACTIVE);
        when(pairs.findByStatus(MentoringService.ACTIVE)).thenReturn(List.of(current));

        Map<String, Object> res = service.suggestions(1L);
        @SuppressWarnings("unchecked") List<Map<String, Object>> matches = (List<Map<String, Object>>) res.get("matches");
        assertEquals(List.of("Asif", "Imran", "Bushra"), matches.stream().map(m -> m.get("name")).toList());
        assertEquals(Set.of("engineering", "computing"), new HashSet<>((List<?>) matches.get(0).get("shared_fields")));
        assertEquals(true, matches.get(1).get("same_chapter"));
        assertFalse(matches.get(0).containsKey("fieldSet"));
    }

    @Test
    void pairingChecksWhatTheMentorOffered() {
        when(volunteers.findById(10L)).thenReturn(Optional.of(volunteer(10, "Asif", false, true, null, "NEW")));
        ApiException e = assertThrows(ApiException.class,
                () -> service.pair(Map.of("studentId", 1, "volunteerId", 10, "help", "MENTOR")));
        assertTrue(e.getMessage().contains("offered to tutor"));

        Mentorship m = service.pair(Map.of("studentId", 1, "volunteerId", 10, "help", "TUTOR", "focus", "Maths"));
        assertEquals(10L, m.getVolunteerId());
        assertEquals("ACTIVE", m.getStatus());
        assertNotNull(m.getStartedOn());
    }

    @Test
    void graduatesMustHaveAgreedToBeContacted() {
        AlumniProfile p = new AlumniProfile();
        p.setUserId(2L);
        p.setConsentToContact(false);
        when(profiles.findById(2L)).thenReturn(Optional.of(p));
        assertThrows(ApiException.class, () -> service.pair(Map.of("studentId", 1, "alumniUserId", 2, "help", "MENTOR")));
        p.setConsentToContact(true);
        assertEquals(2L, service.pair(Map.of("studentId", 1, "alumniUserId", 2, "help", "MENTOR")).getAlumniUserId());
        assertThrows(ApiException.class, () -> service.pair(Map.of("studentId", 1, "help", "MENTOR")));
    }

    @Test
    void endingAPairDatesIt() {
        Mentorship m = new Mentorship();
        m.setId(5L);
        m.setStudentUserId(1L);
        m.setStartedOn(java.time.LocalDate.of(2026, 1, 1));
        when(pairs.findById(5L)).thenReturn(Optional.of(m));
        service.update(5L, Map.of("status", "ENDED", "notes", "Finished the year"));
        assertEquals("ENDED", m.getStatus());
        assertNotNull(m.getEndedOn());
        assertThrows(ApiException.class, () -> service.update(5L, Map.of("status", "ENDED", "endedOn", "2025-12-01")));
    }
}
