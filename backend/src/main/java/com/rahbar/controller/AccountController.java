package com.rahbar.controller;

import com.rahbar.security.AuthUtil;
import com.rahbar.service.AccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The signed-in user's own profile and password (every role). */
@RestController
@RequestMapping("/api/account")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }

    @GetMapping("/profile")
    public Map<String, Object> profile() {
        return accountService.profile(me());
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> body) {
        return accountService.updateProfile(me(), body);
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@RequestBody Map<String, String> body) {
        accountService.changePassword(me(), body.get("currentPassword"), body.get("newPassword"), body.get("confirmPassword"));
        return Map.of("message", "Your password has been changed.");
    }
}
