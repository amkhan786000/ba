package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.entity.Role;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.VolunteerApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Public volunteer sign-ups: what is required, what is kept, flood protection and staff notifications. */
class VolunteerServiceTest {

    private final VolunteerApplicationRepository volunteers = mock(VolunteerApplicationRepository.class);
    private final ChapterRepository chapters = mock(ChapterRepository.class);
    private final RccCenterRepository centers = mock(RccCenterRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final com.rahbar.repository.WebVolunteerOptionRepository options = mock(com.rahbar.repository.WebVolunteerOptionRepository.class);
    private final VolunteerService service = new VolunteerService(volunteers, chapters, centers, roles, notifications, options);

    private static Role role(int id, String permissions) {
        Role r = new Role();
        r.setRoleId(id);
        r.setPermissions(permissions);
        return r;
    }

    @BeforeEach
    void setUp() {
        Chapter darbhanga = new Chapter();
        darbhanga.setChapterId(3L);
        darbhanga.setActive(true);
        when(chapters.findById(3L)).thenReturn(Optional.of(darbhanga));
        when(chapters.findById(99L)).thenReturn(Optional.empty());
        when(centers.existsById(7L)).thenReturn(true);
        when(roles.findAll()).thenReturn(List.of(role(1, ""), role(2, "VOLUNTEERS:VIEW,VOLUNTEERS:EDIT"), role(5, "DASHBOARD:VIEW")));
    }

    private Map<String, Object> form() {
        Map<String, Object> f = new HashMap<>();
        f.put("fullName", "  Sana Khan ");
        f.put("email", "sana@example.com");
        f.put("wantsTutor", true);
        f.put("tutorSubjects", List.of("Maths", "Science"));
        f.put("tutorGrades", "9,10");
        f.put("tutorMode", "IN_PERSON");
        f.put("rccCenterId", 7);
        f.put("hoursPerMonth", "8");
        f.put("wantsChapter", true);
        f.put("chapterId", 3);
        f.put("wantsSponsor", false);
        f.put("sponsorStudents", 4); // ignored: not offering to sponsor
        f.put("source", "biharanjuman.com");
        return f;
    }

    private VolunteerApplication saved() {
        ArgumentCaptor<VolunteerApplication> c = ArgumentCaptor.forClass(VolunteerApplication.class);
        verify(volunteers).save(c.capture());
        return c.getValue();
    }

    @Test
    void savesTheSignUpAndNotifiesStaffWithThePermission() {
        service.submit(form());
        VolunteerApplication v = saved();
        assertEquals("Sana Khan", v.getFullName());
        assertTrue(v.isWantsTutor());
        assertTrue(v.isWantsChapter());
        assertFalse(v.isWantsSponsor());
        assertNull(v.getSponsorStudents());
        assertEquals("Maths,Science", v.getTutorSubjects());
        assertEquals(7L, v.getRccCenterId());
        assertEquals(8, v.getHoursPerMonth());
        assertEquals(3L, v.getChapterId());
        assertEquals("NEW", v.getStatus());
        verify(notifications).notifyRoles(eq(List.of(1, 2)), contains("Sana Khan"), contains("tutor, join a chapter"),
                eq("VOLUNTEER"), eq("/admin/volunteers"));
    }

    private void rejected(Map<String, Object> f, String messagePart) {
        ApiException e = assertThrows(ApiException.class, () -> service.submit(f));
        assertTrue(e.getMessage().contains(messagePart), e.getMessage());
        verify(volunteers, never()).save(any());
    }

    @Test
    void needsANameAContactAndAtLeastOneWayToHelp() {
        Map<String, Object> f = form();
        f.remove("fullName");
        rejected(f, "name");

        Map<String, Object> g = form();
        g.remove("email");
        rejected(g, "email address or a phone");

        Map<String, Object> h = form();
        h.put("wantsTutor", false);
        h.put("wantsChapter", "false");
        rejected(h, "at least one");
    }

    @Test
    void checksChaptersCentersAndNumbers() {
        Map<String, Object> f = form();
        f.put("chapterId", 99);
        rejected(f, "Unknown chapter");

        Map<String, Object> g = form();
        g.put("rccCenterId", 8);
        rejected(g, "Unknown RCC center");

        Map<String, Object> h = form();
        h.put("hoursPerMonth", 500);
        rejected(h, "between 1 and 200");

        Map<String, Object> i = form();
        i.put("tutorMode", "BY_POST");
        rejected(i, "tutoring mode");
    }

    @Test
    void botTrapIsIgnoredAndRepeatsAreLimited() {
        Map<String, Object> f = form();
        f.put("website", "http://spam.example");
        service.submit(f);
        verify(volunteers, never()).save(any());

        when(volunteers.countByEmailIgnoreCaseAndCreatedAtAfterOrPhoneAndCreatedAtAfter(any(), any(), any(), any()))
                .thenReturn((long) VolunteerService.MAX_PER_DAY);
        ApiException e = assertThrows(ApiException.class, () -> service.submit(form()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
    }

    @Test
    void staffSetStatusAndNotes() {
        VolunteerApplication v = new VolunteerApplication();
        when(volunteers.findById(5L)).thenReturn(Optional.of(v));
        service.update(5L, Map.of("status", "CONTACTED", "notes", "Called on Sunday"));
        assertEquals("CONTACTED", v.getStatus());
        assertEquals("Called on Sunday", v.getNotes());
        assertThrows(ApiException.class, () -> service.update(5L, Map.of("status", "MAYBE")));
        assertThrows(ApiException.class, () -> service.update(6L, Map.of("status", "NEW")));
    }

    private static com.rahbar.entity.WebVolunteerOption option(String key, com.rahbar.entity.WebVolunteerOption.Kind kind, boolean active) {
        var o = new com.rahbar.entity.WebVolunteerOption();
        o.setOptionKey(key);
        o.setKind(kind);
        o.setTitle("Organise events");
        o.setChoices("Patna\nOnline");
        o.setActive(active);
        return o;
    }

    @Test
    void anOptionAddedByTheOfficeIsEnoughAndIsKeptAsText() {
        when(options.findByOptionKey("events")).thenReturn(Optional.of(option("events", com.rahbar.entity.WebVolunteerOption.Kind.OTHER, true)));
        Map<String, Object> f = new HashMap<>();
        f.put("fullName", "Sana");
        f.put("phone", "9876543210");
        f.put("otherOffers", List.of(Map.of("key", "events", "choices", List.of("Patna", "Online"))));
        service.submit(f);
        assertEquals("Organise events: Patna, Online", saved().getOtherOffers());
    }

    @Test
    void hiddenOrUnknownOptionsAreRefused() {
        when(options.findByOptionKey("events")).thenReturn(Optional.of(option("events", com.rahbar.entity.WebVolunteerOption.Kind.OTHER, false)));
        when(options.findByOptionKey("nope")).thenReturn(Optional.empty());
        for (String key : List.of("events", "nope")) {
            Map<String, Object> f = new HashMap<>();
            f.put("fullName", "Sana");
            f.put("phone", "9876543210");
            f.put("otherOffers", List.of(Map.of("key", key)));
            ApiException e = assertThrows(ApiException.class, () -> service.submit(f));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
    }
}
