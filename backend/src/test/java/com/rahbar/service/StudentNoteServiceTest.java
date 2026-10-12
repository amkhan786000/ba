package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Role;
import com.rahbar.entity.StudentNote;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Notes to sponsors: a student writes, the office approves or returns, the sponsor reads approved ones. */
class StudentNoteServiceTest {

    private final StudentNoteRepository notes = mock(StudentNoteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final StudentNoteService service = new StudentNoteService(notes, users, mappings, mock(RoleRepository.class), notifications, scope);

    private final User asha = new User();

    @BeforeEach
    void setUp() {
        asha.setId(11L);
        asha.setName("Asha");
        when(users.findById(11L)).thenReturn(Optional.of(asha));
        when(scope.requireStudent(11L)).thenReturn(asha);
        when(notes.save(any())).thenAnswer(i -> i.getArgument(0));
        User office = new User();
        office.setId(2L);
        office.setRoleId(8);
        var p = new RahbarUserPrincipal(office, Set.of("STUDENT_NOTES:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void hasSponsor() {
        GrantorGrantee g = new GrantorGrantee();
        g.setGrantorId(5L);
        g.setGranteeId(11L);
        when(mappings.findFirstByGranteeId(11L)).thenReturn(Optional.of(g));
    }

    @Test
    void aNoteGoesToTheCurrentSponsorAndWaitsForTheOffice() {
        hasSponsor();
        StudentNote n = service.write(11L, Map.of("kind", "THANK_YOU", "message", "Thank you, I passed my exams!"));
        assertEquals(5L, n.getSponsorUserId());
        assertEquals("PENDING", n.getStatus());
        assertEquals("THANK_YOU", n.getKind());
        verify(notifications, never()).notify(eq(5L), anyString(), anyString(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void notesNeedASponsorSomeWordsAndAFewAtATime() {
        assertThrows(ApiException.class, () -> service.write(11L, Map.of("message", "Hello there, sponsor")));
        hasSponsor();
        assertThrows(ApiException.class, () -> service.write(11L, Map.of("message", "hi")));
        assertThrows(ApiException.class, () -> service.write(11L, Map.of("message", "x".repeat(1001))));
        when(notes.countByStudentUserIdAndStatus(11L, "PENDING")).thenReturn(3L);
        assertThrows(ApiException.class, () -> service.write(11L, Map.of("message", "One more update for you")));
    }

    @Test
    void approvingTellsTheSponsorAndReturningNeedsAReason() {
        StudentNote n = new StudentNote();
        n.setId(1L);
        n.setStudentUserId(11L);
        n.setSponsorUserId(5L);
        n.setMessage("I got admission to M.Tech");
        n.setStatus("PENDING");
        when(notes.findById(1L)).thenReturn(Optional.of(n));

        assertThrows(ApiException.class, () -> service.review(1L, Map.of("decision", "RETURN")));
        service.review(1L, Map.of("decision", "APPROVE"));
        assertEquals("APPROVED", n.getStatus());
        assertEquals(2L, n.getReviewedBy());
        verify(notifications).notify(eq(5L), contains("Asha"), anyString(), anyString(), eq("/sponsor/dashboard"),
                eq(EmailType.SPONSOR_STUDENT_NOTE), argThat(v -> "I got admission to M.Tech".equals(v.get("message"))));
        assertThrows(ApiException.class, () -> service.review(1L, Map.of("decision", "APPROVE"))); // checked already
    }
}
