package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.rahbar.service.ServiceSupport.requireUser;

/** The signed-in user's own account: profile and password (every role). */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public AccountService(UserRepository userRepository, ChapterRepository chapterRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    public Map<String, Object> profile(Long userId) {
        User u = requireUser(userRepository, userId, "User not found");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", u.getId());
        p.put("userId", u.getUserId());
        p.put("name", u.getName());
        p.put("email", u.getEmail());
        p.put("phone", u.getPhone());
        p.put("sex", u.getSex());
        p.put("chapterId", u.getChapterId());
        p.put("chapterName", u.getChapterName());
        p.put("year", u.getYear());
        p.put("status", u.getStatus());
        p.put("roleId", u.getRoleId());
        p.put("roleName", roleRepository.findById(u.getRoleId()).map(r -> r.getRoleName()).orElse(null));
        p.put("memberSince", u.getCreatedAt());
        p.put("mustChangePassword", Boolean.TRUE.equals(u.getMustChangePassword()));
        return p;
    }

    /** Name, email, phone, gender and chapter; email and phone must stay unique. */
    public Map<String, Object> updateProfile(Long userId, Map<String, String> body) {
        User u = requireUser(userRepository, userId, "User not found");
        String name = trim(body.get("name"));
        String email = trim(body.get("email"));
        String phone = trim(body.get("phone"));
        if (name == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Name is required.");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }
        if (phone == null || !phone.matches("^[+0-9 ()-]{7,15}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a valid phone number.");
        }
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "That email address is already used by another account.");
        }
        if (userRepository.existsByPhoneAndIdNot(phone, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "That phone number is already used by another account.");
        }
        u.setName(name);
        u.setEmail(email);
        u.setPhone(phone);
        String sex = trim(body.get("sex"));
        if ("M".equals(sex) || "F".equals(sex)) u.setSex(sex);
        if (body.containsKey("chapterId")) u.setChapterId(ServiceSupport.requireChapter(chapterRepository, body.get("chapterId")));
        userRepository.save(u);
        return profile(userId);
    }

    public void changePassword(Long userId, String currentPassword, String newPassword, String confirmPassword) {
        User u = requireUser(userRepository, userId, "User not found");
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your current password is not correct.");
        }
        PasswordPolicy.check(newPassword, confirmPassword);
        if (newPassword.equals(currentPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new password must be different from the current one.");
        }
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(false);
        userRepository.save(u);
        notificationService.notify(userId, "Password changed",
                "Your Rahbar password was changed. If this wasn't you, contact the administrator straight away.",
                NotificationService.ACCOUNT, null, EmailType.PASSWORD_CHANGED, ServiceSupport.vars());
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
