package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserAdditionalRoleRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RahbarUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAdditionalRoleRepository additionalRoles;

    public RahbarUserDetailsService(UserRepository userRepository, RoleRepository roleRepository,
                                    UserAdditionalRoleRepository additionalRoles) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.additionalRoles = additionalRoles;
    }

    @Override
    /** The "username" is users.id (the JWT subject). */
    public UserDetails loadUserByUsername(String id) {
        User user = parseId(id).flatMap(userRepository::findById)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return principalFor(user);
    }

    /** The user with the permissions of all their roles: the main one (users.role_id) and any additional ones. */
    public RahbarUserPrincipal principalFor(User user) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (user.getRoleId() != null) ids.add(user.getRoleId());
        if (user.getId() != null) additionalRoles.findByUserId(user.getId()).forEach(r -> ids.add(r.getRoleId()));
        List<Role> roles = new ArrayList<>();
        for (Integer roleId : ids) roleRepository.findById(roleId).ifPresent(roles::add);
        return combine(user, ids, roles);
    }

    /**
     * Several roles together:
     * <ul>
     *   <li>Permissions: all of them (the Super Admin always gets every permission).</li>
     *   <li>Student-data scope: the widest (ALL, then CHAPTER, then RCC) of the roles that grant admin permissions;
     *       roles without any (sponsor, student, ...) don't widen it.</li>
     *   <li>REB: limited to the user's classes only when every role with REB permissions is a REB_CLASSES role.</li>
     * </ul>
     */
    static RahbarUserPrincipal combine(User user, Set<Integer> roleIds, List<Role> roles) {
        if (roleIds.contains(Role.SUPER_ADMIN)) return new RahbarUserPrincipal(user, Section.allKeys(), Role.SCOPE_ALL, roleIds, false);
        if (roles.size() == 1) { // one role: exactly that role's permissions and scope
            Role r = roles.get(0);
            String scope = r.getScope() == null ? Role.SCOPE_ALL : r.getScope();
            return new RahbarUserPrincipal(user, Section.normalize(r.permissionSet()), scope, roleIds, Role.SCOPE_REB_CLASSES.equals(scope));
        }
        Set<String> permissions = new HashSet<>();
        Map<Role, Set<String>> byRole = new LinkedHashMap<>();
        for (Role r : roles) {
            Set<String> p = Section.normalize(r.permissionSet());
            byRole.put(r, p);
            permissions.addAll(p);
        }
        boolean anyAll = false, anyChapter = false, anyRcc = false, anyRebClasses = false, rebElsewhere = false;
        for (Map.Entry<Role, Set<String>> e : byRole.entrySet()) {
            String scope = e.getKey().getScope() == null ? Role.SCOPE_ALL : e.getKey().getScope();
            Set<String> p = e.getValue();
            if (Role.SCOPE_REB_CLASSES.equals(scope)) {
                anyRebClasses = true;
                continue;
            }
            if (p.stream().anyMatch(k -> k.startsWith("REB_"))) rebElsewhere = true;
            if (p.isEmpty()) continue;
            switch (scope) {
                case Role.SCOPE_CHAPTER -> anyChapter = true;
                case Role.SCOPE_RCC -> anyRcc = true;
                default -> anyAll = true;
            }
        }
        boolean rebClassesOnly = anyRebClasses && !rebElsewhere;
        String scope = anyAll ? Role.SCOPE_ALL : anyChapter ? Role.SCOPE_CHAPTER : anyRcc ? Role.SCOPE_RCC
                : rebClassesOnly ? Role.SCOPE_REB_CLASSES : Role.SCOPE_ALL;
        return new RahbarUserPrincipal(user, permissions, scope, roleIds, rebClassesOnly);
    }

    private static java.util.Optional<Long> parseId(String id) {
        try {
            return java.util.Optional.of(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty(); // e.g. a token issued before user ids were numeric
        }
    }
}
