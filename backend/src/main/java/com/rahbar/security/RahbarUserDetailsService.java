package com.rahbar.security;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RahbarUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public RahbarUserDetailsService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    /** The "username" is users.id (the JWT subject). */
    public UserDetails loadUserByUsername(String id) {
        User user = parseId(id).flatMap(userRepository::findById)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return principalFor(user);
    }

    /** The user with their role's permissions (the Super Admin always gets every permission). */
    public RahbarUserPrincipal principalFor(User user) {
        if (Integer.valueOf(Role.SUPER_ADMIN).equals(user.getRoleId())) {
            return new RahbarUserPrincipal(user, Section.allKeys(), Role.SCOPE_ALL);
        }
        Role role = user.getRoleId() == null ? null : roleRepository.findById(user.getRoleId()).orElse(null);
        if (role == null) return new RahbarUserPrincipal(user, java.util.Set.of(), Role.SCOPE_ALL);
        return new RahbarUserPrincipal(user, Section.normalize(role.permissionSet()), role.getScope());
    }

    private static java.util.Optional<Long> parseId(String id) {
        try {
            return java.util.Optional.of(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty(); // e.g. a token issued before user ids were numeric
        }
    }
}
