package com.rahbar.controller;

import com.rahbar.dto.*;
import com.rahbar.entity.User;
import com.rahbar.service.AuthService;
import com.rahbar.web.ActivityLogInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mirrors routes/auth.py: login (+ OTP step-up), logout (client-side token
 * discard, since auth is now stateless JWT instead of Flask sessions),
 * password reset and self-registration.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest req, HttpServletRequest request) {
        return authService.login(req, ActivityLogInterceptor.clientIp(request));
    }

    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@RequestBody OtpVerifyRequest req, HttpServletRequest request) {
        return authService.verifyOtp(req, ActivityLogInterceptor.clientIp(request));
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody RegisterRequest req) {
        authService.register(req);
        return Map.of("message", "Registration successful! Please log in.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return Map.of("message", "Password reset successfully!");
    }

    @GetMapping("/me")
    public User me(@RequestHeader("Authorization") String authHeader) {
        return authService.userForToken(authHeader.replace("Bearer ", ""));
    }
}
