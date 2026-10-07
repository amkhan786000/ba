package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The signed-in user. Authorities are ROLE_<roleId> (portal screens) plus the role's permission keys such as
 * "USERS:EDIT" (admin screens, see Section), read fresh from the database on every request.
 */
public class RahbarUserPrincipal implements UserDetails {
    private final User user;
    private final Set<String> permissions;
    private final String scope;

    public RahbarUserPrincipal(User user, Set<String> permissions, String scope) {
        this.user = user;
        this.permissions = Set.copyOf(permissions);
        this.scope = scope == null ? Role.SCOPE_ALL : scope;
    }

    /** Permission keys (EDIT already implies VIEW; the Super Admin has all of them). */
    public Set<String> getPermissions() {
        return permissions;
    }

    /** ALL, CHAPTER (own chapter only) or RCC (own RCC center only). */
    public String getScope() {
        return scope;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRoleId()));
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return String.valueOf(user.getId());
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return !"Inactive".equalsIgnoreCase(user.getStatus()); }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
