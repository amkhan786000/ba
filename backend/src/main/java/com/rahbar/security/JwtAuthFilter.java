package com.rahbar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RahbarUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, RahbarUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtService.isTokenValid(token)) {
                    String id = jwtService.extractUserId(token);
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(id);
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // Accounts created with the default password may only change it (and sign out) until they do.
                        if (userDetails instanceof RahbarUserPrincipal p
                                && Boolean.TRUE.equals(p.getUser().getMustChangePassword())
                                && !allowedBeforePasswordChange(request.getRequestURI())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Please change your password before continuing.\","
                                    + "\"code\":\"PASSWORD_CHANGE_REQUIRED\"}");
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                // invalid/expired token -> request proceeds unauthenticated
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean allowedBeforePasswordChange(String path) {
        return path == null || !path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/account/")
                || path.startsWith("/api/public/");
    }
}
