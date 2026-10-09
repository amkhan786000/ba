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
    private final com.rahbar.repository.RoleRepository roleRepository;

    public AccountController(AccountService accountService, com.rahbar.repository.RoleRepository roleRepository) {
        this.accountService = accountService;
        this.roleRepository = roleRepository;
    }

    private static Long me() {
        return AuthUtil.currentUser().getId();
    }

    /** What the signed-in user may do on the admin screens: permission keys, scope and their chapter / RCC center. */
    @GetMapping("/access")
    public Map<String, Object> access() {
        var p = com.rahbar.security.Access.current();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("roleId", p.getUser().getRoleId());
        body.put("roleName", roleRepository.findById(p.getUser().getRoleId()).map(com.rahbar.entity.Role::getRoleName).orElse(null));
        body.put("permissions", p.getPermissions());
        body.put("scope", p.getScope());
        body.put("chapterId", p.getUser().getChapterId());
        body.put("rccCenterId", p.getUser().getRccCenterId());
        // No address, or a placeholder (...@rahbar.com): the app shows a banner asking to add a real one.
        String email = p.getUser().getEmail();
        body.put("emailMissing", email == null || !email.contains("@")
                || email.trim().toLowerCase(java.util.Locale.ROOT).endsWith("@rahbar.com"));
        return body;
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
