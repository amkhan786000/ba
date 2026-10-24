package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Student Directory > Delete: only the Super Admin, and only a student with nothing but their user and their
 * application / family details (which go with them).
 */
class StudentDeletionTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserDeletionService service = new UserDeletionService(jdbc, users);
    /** "table.column" -> users.id -> rows. */
    private final Map<String, Map<Long, Long>> rows = new HashMap<>();

    private void signIn(int role) {
        User me = new User();
        me.setId(1L);
        me.setRoleId(role);
        var p = new RahbarUserPrincipal(me, Set.of("STUDENTS:EDIT"), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    @BeforeEach
    void schema() {
        User s = new User();
        s.setId(5L);
        s.setRoleId(6);
        when(users.findById(5L)).thenReturn(Optional.of(s));
        when(jdbc.queryForList(contains("information_schema.columns"), eq(String.class)))
                .thenReturn(List.of("grantee_details.user_id", "payments.grantee_id", "bank_details.user_id", "activity_log.user_id", "notifications.user_id"));
        when(jdbc.queryForList(contains("key_column_usage"), eq(String.class))).thenReturn(List.of("notifications.user_id"));
        when(jdbc.queryForList(contains("GROUP BY"), any(Object[].class))).thenAnswer(i -> {
            String sql = i.getArgument(0);
            String t = sql.substring(sql.indexOf("FROM `") + 6, sql.indexOf('`', sql.indexOf("FROM `") + 6));
            String c = sql.substring(sql.indexOf("WHERE `") + 7, sql.indexOf('`', sql.indexOf("WHERE `") + 7));
            List<Map<String, Object>> out = new ArrayList<>();
            rows.getOrDefault(t + "." + c, Map.of()).forEach((id, n) -> out.add(Map.of("uid", id, "n", n)));
            return out;
        });
        when(jdbc.queryForList(contains("user_additional_roles"), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForList(startsWith("SELECT id FROM users WHERE role_id <> 6"), any(Object[].class))).thenReturn(List.of());
    }

    @Test
    void aStudentWithOnlyTheirApplicationDetailsCanBeDeleted() {
        signIn(Role.SUPER_ADMIN);
        rows.put("grantee_details.user_id", Map.of(5L, 1L)); // their own: not a reason
        rows.put("payments.grantee_id", Map.of(6L, 2L));     // someone else's payments
        assertEquals(List.of(), service.studentBlockers(List.of(5L)).get(5L));
        service.deleteStudent(5L);
        verify(jdbc).update("DELETE FROM `grantee_details` WHERE `user_id` = ?", 5L);
        verify(jdbc).update("DELETE FROM users WHERE id = ?", 5L);
    }

    @Test
    void paymentsOrBankDetailsKeepTheStudent() {
        signIn(Role.SUPER_ADMIN);
        rows.put("payments.grantee_id", Map.of(5L, 2L));
        rows.put("bank_details.user_id", Map.of(5L, 1L));
        List<String> why = service.studentBlockers(List.of(5L, 7L)).get(5L);
        assertTrue(why.contains("2 payment(s) received as student"));
        assertTrue(why.contains("1 bank details"));
        assertEquals(List.of(), service.studentBlockers(List.of(5L, 7L)).get(7L));
        assertThrows(ApiException.class, () -> service.deleteStudent(5L));
        verify(jdbc, never()).update(startsWith("DELETE FROM users"), any(Object[].class));
    }

    @Test
    void onlyTheSuperAdmin() {
        signIn(2);
        assertThrows(ApiException.class, () -> service.deleteStudent(5L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
