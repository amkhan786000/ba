package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A user can be deleted (Super Admin only) when nothing links to them; otherwise the reasons are listed. */
class UserDeletionServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserDeletionService service = new UserDeletionService(jdbc, users);
    /** "table.column" -> rows pointing to user 5. */
    private final Map<String, Long> rows = new HashMap<>();

    private void signIn(int role) {
        User me = new User();
        me.setId(1L);
        me.setRoleId(role);
        var p = new RahbarUserPrincipal(me, Set.of("USERS:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private User target(Long chapterId) {
        User u = new User();
        u.setId(5L);
        u.setUserId("U-5");
        u.setRoleId(5);
        u.setChapterId(chapterId);
        when(users.findById(5L)).thenReturn(Optional.of(u));
        when(jdbc.queryForList(contains("information_schema.columns"), eq(String.class)))
                .thenReturn(List.of("grantor_grantees.grantor_id", "payments.grantor_id", "notifications.user_id", "activity_log.user_id",
                        "web_pages.created_by", "users.created_by", "trusted_devices.user_id"));
        when(jdbc.queryForList(contains("key_column_usage"), eq(String.class)))
                .thenReturn(List.of("payments.grantor_id", "notifications.user_id", "web_pages.created_by", "users.created_by", "trusted_devices.user_id"));
        when(jdbc.queryForList(contains("GROUP BY"), any(Object[].class))).thenAnswer(i -> {
            String sql = i.getArgument(0);
            String t = sql.substring(sql.indexOf("FROM `") + 6, sql.indexOf('`', sql.indexOf("FROM `") + 6));
            String c = sql.substring(sql.indexOf("WHERE `") + 7, sql.indexOf('`', sql.indexOf("WHERE `") + 7));
            long n = rows.getOrDefault(t + "." + c, 0L);
            return n == 0 ? List.of() : List.of(Map.<String, Object>of("uid", 5L, "n", n));
        });
        return u;
    }

    @Test
    void onlyTheSuperAdminMayDelete() {
        signIn(2);
        target(null);
        assertThrows(ApiException.class, () -> service.blockers(5L));
    }

    @Test
    void linksAreListedAndNothingIsDeleted() {
        signIn(Role.SUPER_ADMIN);
        target(3L);
        rows.put("grantor_grantees.grantor_id", 2L);
        rows.put("web_pages.created_by", 1L);
        rows.put("notifications.user_id", 4L); // goes with the user: not a reason
        List<String> why = service.blockers(5L);
        assertTrue(why.stream().anyMatch(w -> w.startsWith("Belongs to a chapter")));
        assertTrue(why.contains("2 student(s) mapped to them as sponsor"));
        assertTrue(why.contains("1 web pages record(s) they created or changed"));
        assertEquals(3, why.size());
        assertThrows(ApiException.class, () -> service.delete(5L));
        verify(jdbc, never()).update(startsWith("DELETE FROM users"), any(Object[].class));
    }

    @Test
    void anUnlinkedUserIsDeletedWithTheirSignInRows() {
        signIn(Role.SUPER_ADMIN);
        target(null);
        assertEquals(List.of(), service.blockers(5L));
        service.delete(5L);
        verify(jdbc).update("UPDATE `activity_log` SET `user_id` = NULL WHERE `user_id` = ?", 5L);
        verify(jdbc).update("DELETE FROM `trusted_devices` WHERE `user_id` = ?", 5L);
        verify(jdbc).update("DELETE FROM `notifications` WHERE `user_id` = ?", 5L);
        verify(jdbc).update("DELETE FROM users WHERE id = ?", 5L);
    }

    @Test
    void notYourOwnAccount() {
        signIn(Role.SUPER_ADMIN);
        User me = target(null);
        me.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(me));
        assertTrue(service.blockers(1L).contains("This is your own account."));
    }

    @Test
    void aWholePageIsCheckedAtOnce() {
        signIn(Role.SUPER_ADMIN);
        User linked = target(null);
        User free = new User();
        free.setId(9L);
        free.setRoleId(5);
        rows.put("payments.grantor_id", 3L); // user 5 only
        Map<Long, List<String>> why = service.blockers(List.of(linked, free));
        assertEquals(List.of("3 payment(s) made as sponsor"), why.get(5L));
        assertEquals(List.of(), why.get(9L));
        verify(jdbc).queryForList(contains("`payments` WHERE `grantor_id` IN (?,?)"), any(Object[].class));
    }
}
