package com.rahbar.service;

import com.rahbar.dto.OnBehalf;
import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.SponsorMappingHistoryRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * An anonymous sponsor is a real sponsor: the sponsor who mapped the student stays the sponsor of record; only the
 * Super Admin and the Application Administrator see (and choose) who the anonymous sponsor is.
 */
class AnonymousSponsorTest {

    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SponsorMappingHistoryRepository history = mock(SponsorMappingHistoryRepository.class);
    private final SponsorMappingService service = new SponsorMappingService(mappings, users, mock(NotificationService.class),
            mock(PaymentInstallmentService.class), history);
    private final User donor = user(30, "1042", "Asha Khan", 5L);

    private User user(long id, String code, String name, Long chapterId) {
        User u = new User();
        u.setId(id);
        u.setUserId(code);
        u.setName(name);
        u.setChapterId(chapterId);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static void signIn(int roleId) {
        User me = new User();
        me.setId(1L);
        me.setRoleId(roleId);
        var p = new RahbarUserPrincipal(me, Set.of("SPONSORSHIPS:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private GrantorGrantee onBehalf(Long donorUserId) {
        GrantorGrantee gg = new GrantorGrantee();
        gg.setGranteeId(10L);
        gg.setGrantorId(20L);
        gg.setOnBehalf(true);
        gg.setDonorChapterId(5L);
        gg.setDonorUserId(donorUserId);
        return gg;
    }

    @Test
    void onlyAdminsSeeWhoItIs() {
        Map<Long, String> chapters = Map.of(5L, "Jeddah Chapter");
        signIn(3); // a Chapter Lead
        Map<String, Object> m = SponsorMappingService.onBehalfFields(onBehalf(30L), chapters, id -> users.findById(id).orElse(null));
        assertEquals("on behalf of an anonymous sponsor from Jeddah Chapter", m.get("on_behalf_text"));
        assertFalse(m.containsKey("donor_name"));
        assertFalse(m.containsKey("donor_user_id"));

        for (int admin : new int[]{1, 2}) {
            signIn(admin);
            m = SponsorMappingService.onBehalfFields(onBehalf(30L), chapters, id -> users.findById(id).orElse(null));
            assertEquals("Asha Khan", m.get("donor_name"));
            assertEquals(30L, m.get("donor_user_id"));
            assertTrue(String.valueOf(m.get("on_behalf_text")).contains("Asha Khan (1042)"));
        }
    }

    @Test
    void onlyAdminsMayChooseTheAnonymousSponsor() {
        Map<String, Object> body = new HashMap<>(Map.of("onBehalf", true, "donorChapterId", 5));
        body.put("donorUserId", 30);
        signIn(8); // Office Coordinator
        assertThrows(ApiException.class, () -> AdminService.onBehalf(body));
        signIn(2);
        OnBehalf ob = AdminService.onBehalf(body);
        assertEquals(30L, ob.donorUserId());
        assertTrue(ob.setsDonor());

        body.remove("donorChapterId"); // with the account, the chapter can be left out (theirs is used)
        assertDoesNotThrow(() -> AdminService.onBehalf(body));
        body.remove("donorUserId");
        assertThrows(ApiException.class, () -> AdminService.onBehalf(body)); // neither: the chapter is needed
    }

    @Test
    void someoneElseChangingTheSponsorshipKeepsTheLinkedAccount() {
        GrantorGrantee gg = onBehalf(30L);
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.of(gg));
        when(history.findByGranteeIdAndEndedAtIsNull(10L)).thenReturn(List.of());
        user(20, "SP-20", "Mapper", null);
        service.map(10L, 20L, "Accepted", false, null, new OnBehalf(5L, "Donor B"), true); // label changed, no account sent
        assertEquals(30L, gg.getDonorUserId());
        assertEquals("Donor B", gg.getDonorLabel());
        service.map(10L, 20L, "Accepted", false, null, new OnBehalf(5L, null, null, true), true); // an admin unlinks it
        assertNull(gg.getDonorUserId());
    }

    @Test
    void adminsLinkTheAccountLaterAndItsChapterIsUsed() {
        GrantorGrantee gg = onBehalf(null);
        gg.setDonorChapterId(9L);
        when(mappings.findFirstByGranteeId(10L)).thenReturn(Optional.of(gg));
        when(history.findByGranteeIdAndEndedAtIsNull(10L)).thenReturn(List.of());
        signIn(3);
        assertThrows(ApiException.class, () -> service.linkAnonymousSponsor(10L, 30L));
        signIn(1);
        assertThrows(ApiException.class, () -> service.linkAnonymousSponsor(10L, 20L)); // not the mapper
        service.linkAnonymousSponsor(10L, 30L);
        assertEquals(30L, gg.getDonorUserId());
        assertEquals(5L, gg.getDonorChapterId()); // the anonymous sponsor's own chapter
        verify(mappings).save(any(GrantorGrantee.class));
    }
}
