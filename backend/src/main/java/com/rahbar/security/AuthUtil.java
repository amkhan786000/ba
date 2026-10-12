package com.rahbar.security;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtil {
    private AuthUtil() {}

    public static User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof RahbarUserPrincipal p) {
            return p.getUser();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }

    public static void requireRole(int... allowedRoleIds) {
        RahbarUserPrincipal me = Access.current(); // any of the user's roles (main or additional) will do
        for (int r : allowedRoleIds) {
            if (me.hasRole(r)) return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to access this page.");
    }
}
