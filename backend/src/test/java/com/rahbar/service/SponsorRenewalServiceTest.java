package com.rahbar.service;

import com.rahbar.entity.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Sponsor renewal: who is asked, the answers and the summary. */
class SponsorRenewalServiceTest {

    private final RenewalCampaignRepository campaigns = mock(RenewalCampaignRepository.class);
    private final RenewalResponseRepository responses = mock(RenewalResponseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final SponsorRenewalService service = new SponsorRenewalService(campaigns, responses, users, mappings, scope, mock(EmailService.class), "http://x");

    @BeforeEach
    void signIn() {
        User me = new User();
        me.setId(5L);
        me.setRoleId(5);
        var p = new RahbarUserPrincipal(me, Set.of(), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static RenewalResponse response(int students, String answer, Integer wanted) {
        RenewalResponse r = new RenewalResponse();
        r.setCurrentStudents(students);
        r.setAnswer(answer);
        r.setStudentsWanted(wanted);
        return r;
    }

    @Test
    void summaryCountsStudentsCoveredNextSession() {
        Map<String, Object> s = SponsorRenewalService.summary(List.of(response(2, "CONTINUE", null), response(1, "CHANGE", 3),
                response(4, "STOP", null), response(1, null, null)));
        assertEquals(4, s.get("asked"));
        assertEquals(1L, s.get("waiting"));
        assertEquals(8L, s.get("students_now"));
        assertEquals(5L, s.get("students_next"));
    }

    private static User sponsor(long id, String status) {
        User u = new User();
        u.setId(id);
        u.setRoleId(5);
        u.setStatus(status);
        return u;
    }

    private static GrantorGrantee map(long sponsor, long student) {
        GrantorGrantee g = new GrantorGrantee();
        g.setGrantorId(sponsor);
        g.setGranteeId(student);
        return g;
    }

    @Test
    void asksOnlyActiveSponsorsWithStudents() {
        when(campaigns.save(any())).thenAnswer(i -> { RenewalCampaign c = i.getArgument(0); c.setId(1L); return c; });
        when(responses.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.findByRoleId(5)).thenReturn(List.of(sponsor(20, "active"), sponsor(21, "active"), sponsor(22, "Inactive")));
        when(mappings.findAll()).thenReturn(List.of(map(20, 100), map(20, 101), map(22, 102)));
        service.start(new HashMap<>(Map.of("sessionYear", 2027)));
        verify(responses, times(1)).save(argThat(r -> r.getSponsorId() == 20L && r.getCurrentStudents() == 2));
    }

    @Test
    void oneOpenCampaignAtATimeAndAChangeNeedsANumber() {
        RenewalCampaign open = new RenewalCampaign();
        open.setId(1L);
        when(campaigns.findFirstByClosedAtIsNullOrderByIdDesc()).thenReturn(Optional.of(open));
        assertThrows(ApiException.class, () -> service.start(new HashMap<>(Map.of("sessionYear", 2027))));
        RenewalResponse mine = response(2, null, null);
        when(responses.findByCampaignIdAndSponsorId(1L, 5L)).thenReturn(Optional.of(mine));
        assertThrows(ApiException.class, () -> service.answerMine(new HashMap<>(Map.of("answer", "CHANGE"))));
        service.answerMine(new HashMap<>(Map.of("answer", "change", "studentsWanted", 3)));
        assertEquals("CHANGE", mine.getAnswer());
        assertEquals(3, mine.getStudentsWanted());
        assertEquals(5L, mine.getAnsweredBy());
    }
}
