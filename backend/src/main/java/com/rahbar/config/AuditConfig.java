package com.rahbar.config;

import com.rahbar.security.RahbarUserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Turns on JPA auditing: created_by / updated_by come from the logged-in user's id (users.id). */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> Optional.ofNullable(currentUserId());
    }

    /** The logged-in user's id (users.id), or null for unauthenticated requests (login, registration, OTP). */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof RahbarUserPrincipal p) {
            return p.getUser().getId();
        }
        return null;
    }
}
