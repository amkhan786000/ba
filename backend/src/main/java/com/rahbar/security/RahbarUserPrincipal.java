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
 * The signed-in user. Authorities are ROLE_<roleId> for each of their roles (portal screens) plus the permission
 * keys of all their roles such as "USERS:EDIT" (admin screens, see Section), read fresh from the database on every
 * request.
 */
public class RahbarUserPrincipal implements UserDetails {
    private final User user;
    private final Set<String> permissions;
    private final String scope;
    private final Set<Integer> roleIds;
    private final boolean rebClassesOnly;

    /** One role: the scope is that role's (REB_CLASSES limits REB to the user's classes). */
    public RahbarUserPrincipal(User user, Set<String> permissions, String scope) {
        this(user, permissions, scope, user.getRoleId() == null ? Set.of() : Set.of(user.getRoleId()), Role.SCOPE_REB_CLASSES.equals(scope));
    }

    /**
     * Several roles: scope is the student-data scope of them together (ALL, CHAPTER or RCC; REB_CLASSES when the
     * user's only limit is the REB classes they teach), rebClassesOnly whether their REB access covers only their classes.
     */
    public RahbarUserPrincipal(User user, Set<String> permissions, String scope, Set<Integer> roleIds, boolean rebClassesOnly) {
        this.user = user;
        this.permissions = Set.copyOf(permissions);
        this.scope = scope == null ? Role.SCOPE_ALL : scope;
        this.roleIds = Set.copyOf(roleIds);
        this.rebClassesOnly = rebClassesOnly;
    }

    /** The main role (users.role_id) and any additional ones. */
    public Set<Integer> getRoleIds() {
        return roleIds;
    }

    public boolean hasRole(int roleId) {
        return roleIds.contains(roleId);
    }

    /** True when the user's REB access only covers the classes they teach. */
    public boolean isRebClassesOnly() {
        return rebClassesOnly;
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
        roleIds.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
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
