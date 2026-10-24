package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Events: who sees an event in their portal, places, and who may change it. */
class EventServiceTest {

    private final EventRepository events = mock(EventRepository.class);
    private final EventRegistrationRepository registrations = mock(EventRegistrationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EventService service = new EventService(events, registrations, users, mock(CoachingStudentRepository.class),
            mock(ChapterRepository.class), mock(CoachingAccess.class), mock(NotificationService.class));

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static Event event(long id, String audience, Long chapterId, Long centerId, Integer capacity) {
        Event e = new Event();
        e.setId(id);
        e.setTitle("E" + id);
        e.setAudience(audience);
        e.setChapterId(chapterId);
        e.setRccCenterId(centerId);
        e.setCapacity(capacity);
        e.setStartsAt(LocalDateTime.now().plusDays(3));
        return e;
    }

    private static User student(long chapter) {
        User u = new User();
        u.setId(50L);
        u.setRoleId(6);
        u.setChapterId(chapter);
        return u;
    }

    @Test
    void aStudentSeesStudentAndParentsEventsOfTheirChapterOrEveryone() {
        Event forScholars = event(1, "SCHOLARS", null, null, null), forSponsors = event(2, "SPONSORS", null, null, null),
                otherChapter = event(3, "ALL", 9L, null, null), myChapter = event(4, "PARENTS", 7L, null, null),
                coaching = event(5, "ALL", null, 3L, null);
        Event past = event(6, "ALL", null, null, null);
        past.setStartsAt(LocalDateTime.now().minusDays(2));
        when(events.findAllByOrderByStartsAtDesc()).thenReturn(List.of(forScholars, forSponsors, otherChapter, myChapter, coaching, past));
        List<Object> ids = service.forMe(student(7)).stream().map(r -> r.get("id")).toList();
        assertEquals(Set.of(1L, 4L), new HashSet<>(ids));
    }

    @Test
    void noPlaceWhenTheEventIsFull() {
        Event full = event(1, "SCHOLARS", null, null, 1);
        when(events.findAllByOrderByStartsAtDesc()).thenReturn(List.of(full));
        when(events.findById(1L)).thenReturn(Optional.of(full));
        EventRegistration taken = new EventRegistration();
        taken.setEventId(1L);
        when(registrations.findByEventId(1L)).thenReturn(List.of(taken));
        when(registrations.findAll()).thenReturn(List.of(taken));
        when(registrations.findByEventIdAndUserId(1L, 50L)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.register(student(7), 1L, true));
        assertTrue(e.getMessage().contains("full"));
    }

    @Test
    void aChapterLeadChangesOnlyTheirChaptersEvents() {
        Phase3Support.signIn(2, 9, com.rahbar.entity.Role.SCOPE_CHAPTER, 7L, "EVENTS:VIEW", "EVENTS:EDIT");
        assertTrue(service.mayEdit(event(1, "ALL", 7L, null, null)));
        assertFalse(service.mayEdit(event(2, "ALL", null, null, null))); // organisation-wide: sees it, can't change it
        assertTrue(service.visible(event(2, "ALL", null, null, null)));
        assertFalse(service.visible(event(3, "ALL", 9L, null, null)));
    }

    @Test
    void anEventCantEndBeforeItStarts() {
        Phase3Support.signIn(1, 1, null, null, "EVENTS:VIEW", "EVENTS:EDIT");
        Map<String, Object> b = new HashMap<>(Map.of("title", "Workshop", "startsAt", "2026-12-01T10:00", "endsAt", "2026-12-01T09:00"));
        assertThrows(ApiException.class, () -> service.save(null, b));
        verify(events, never()).save(any());
    }
}
