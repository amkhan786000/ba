package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.security.RahbarUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/** Signing in a test user with a role, scope and permissions. */
final class Phase3Support {
    private Phase3Support() {}

    static User signIn(long id, int role, String scope, Long chapterId, String... permissions) {
        User u = new User();
        u.setId(id);
        u.setRoleId(role);
        u.setName("User " + id);
        u.setStatus("active");
        u.setChapterId(chapterId);
        var p = new RahbarUserPrincipal(u, Set.of(permissions), scope == null ? Role.SCOPE_ALL : scope);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        return u;
    }
}
