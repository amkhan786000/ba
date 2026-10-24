package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Helpline: harassment is confidential and urgent, confidential issues stay with the Super Admin and assignee. */
class HelplineServiceTest {

    private final HelplineTicketRepository tickets = mock(HelplineTicketRepository.class);
    private final HelplineMessageRepository messages = mock(HelplineMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final HelplineService service = new HelplineService(tickets, messages, users, mock(RoleRepository.class), notifications);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private User student() {
        User u = new User();
        u.setId(50L);
        u.setRoleId(6);
        u.setName("Sana");
        return u;
    }

    @Test
    void aHarassmentReportIsConfidentialAndUrgentAndGoesToTheSuperAdmin() {
        when(tickets.save(any())).thenAnswer(i -> { HelplineTicket t = i.getArgument(0); t.setId(9L); return t; });
        service.raise(student(), new HashMap<>(Map.of("category", "HARASSMENT", "subject", "Help", "message", "Something happened")));
        verify(tickets, atLeastOnce()).save(argThat(t -> t.isConfidential() && "URGENT".equals(t.getPriority())));
        verify(notifications).notifyRoles(eq(List.of(Role.SUPER_ADMIN)), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void studentsAndSponsorsHaveTheirOwnTopics() {
        assertTrue(HelplineService.categoriesFor(HelplineService.STUDENT).containsKey("HARASSMENT"));
        assertFalse(HelplineService.categoriesFor(HelplineService.SPONSOR).containsKey("HARASSMENT"));
        assertTrue(HelplineService.categoriesFor(HelplineService.SPONSOR).containsKey("PAYMENT"));
        when(tickets.save(any())).thenAnswer(i -> { HelplineTicket t = i.getArgument(0); t.setId(9L); return t; });
        assertThrows(ApiException.class, () -> service.raise(student(), new HashMap<>(Map.of("category", "PAYMENT", "subject", "x", "message", "y"))));
    }

    @Test
    void confidentialIssuesOnlyForTheSuperAdminAndTheAssignee() {
        HelplineTicket t = new HelplineTicket();
        t.setConfidential(true);
        t.setAssignedTo(7L);
        Phase3Support.signIn(3, 2, null, null, "HELPLINE:VIEW");
        assertFalse(service.maySee(t, 3L));
        assertTrue(service.maySee(t, 7L));
        Phase3Support.signIn(1, 1, null, null);
        assertTrue(service.maySee(t, 1L));
        t.setConfidential(false);
        Phase3Support.signIn(3, 2, null, null, "HELPLINE:VIEW");
        assertTrue(service.maySee(t, 3L));
    }

    @Test
    void writingAgainReopensAResolvedIssue() {
        HelplineTicket t = new HelplineTicket();
        t.setId(9L);
        t.setRaisedBy(50L);
        t.setStatus("RESOLVED");
        t.setAssignedTo(7L);
        when(tickets.findById(9L)).thenReturn(Optional.of(t));
        service.replyMine(student(), 9L, new HashMap<>(Map.of("message", "Still not fixed")));
        assertEquals("OPEN", t.getStatus());
        verify(notifications).notify(eq(7L), anyString(), anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void someoneElsesIssueIsNotFound() {
        HelplineTicket t = new HelplineTicket();
        t.setId(9L);
        t.setRaisedBy(99L);
        when(tickets.findById(9L)).thenReturn(Optional.of(t));
        assertThrows(ApiException.class, () -> service.mineDetail(student(), 9L));
    }
}
