package com.rahbar.security;

import com.rahbar.entity.User;
import com.rahbar.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RahbarUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public RahbarUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    /** The "username" is users.id (the JWT subject). */
    public UserDetails loadUserByUsername(String id) {
        User user = parseId(id).flatMap(userRepository::findById)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return new RahbarUserPrincipal(user);
    }

    private static java.util.Optional<Long> parseId(String id) {
        try {
            return java.util.Optional.of(Long.valueOf(id));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty(); // e.g. a token issued before user ids were numeric
        }
    }
}
