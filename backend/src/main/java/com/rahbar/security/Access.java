package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Permission checks for the signed-in user inside services (record-level scope; endpoint checks use @PreAuthorize). */
public final class Access {

    private Access() {}

    public static RahbarUserPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof RahbarUserPrincipal p) return p;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }

    public static boolean can(Section section, Section.Level level) {
        return current().getPermissions().contains(section.key(level));
    }

    public static boolean isSuperAdmin() {
        return Integer.valueOf(Role.SUPER_ADMIN).equals(current().getUser().getRoleId());
    }

    /** True when the user's permissions only cover their own chapter. */
    public static boolean chapterScoped() {
        return Role.SCOPE_CHAPTER.equals(current().getScope());
    }

    /** True when the user's permissions only cover their own RCC center. */
    public static boolean rccScoped() {
        return Role.SCOPE_RCC.equals(current().getScope());
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }
}
