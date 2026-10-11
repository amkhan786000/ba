package com.rahbar.controller;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.security.Section;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/** Signs a fake user in for service-level tests (permission keys, scope, chapter / center). */
final class WebsiteTestUsers {

    private WebsiteTestUsers() {
    }

    static User signIn(long id, String scope, Long chapterId, Long centerId, String... permissions) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRoleId(2);
        u.setChapterId(chapterId);
        u.setRccCenterId(centerId);
        var principal = new RahbarUserPrincipal(u, Section.normalize(Set.of(permissions)), scope);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return u;
    }

    static User signIn(long id, String... permissions) {
        return signIn(id, Role.SCOPE_ALL, null, null, permissions);
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
