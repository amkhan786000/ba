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

    /** Step 1: emails a reset code. The answer is the same whether or not the email has an account. */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        authService.requestPasswordReset(body.get("email"), ActivityLogInterceptor.clientIp(request));
        return Map.of("message", "If that email belongs to an account, a reset code has been sent to it. It is valid for 15 minutes.");
    }

    /** Step 2: { email, code, newPassword, confirmPassword }. */
    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody ResetPasswordRequest req, HttpServletRequest request) {
        authService.resetPassword(req, ActivityLogInterceptor.clientIp(request));
        return Map.of("message", "Your password has been changed. You can sign in now.");
    }

    @GetMapping("/me")
    public User me(@RequestHeader("Authorization") String authHeader) {
        return authService.userForToken(authHeader.replace("Bearer ", ""));
    }
}
