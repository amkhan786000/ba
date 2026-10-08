package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Makes a brand-new (empty) database usable on start-up:
 *  1. adds the built-in roles 1-8 that are missing (existing roles are never changed);
 *  2. when there is no Super Admin yet, creates one for ADMIN_EMAIL (or MAIL_USERNAME) with a random temporary
 *     password, e-mails it to that address and prints it once in the backend log. The admin must change it at
 *     first sign-in. Nothing happens on a database that already has a Super Admin.
 */
@Service
public class BootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);
    private static final int SUPER_ADMIN = 1;

    private static final Object[][] BUILT_IN_ROLES = {
            {1, "Super Admin", "Overall control of the system"},
            {2, "Application Administrator", "Day-to-day operations management"},
            {3, "Application Coordinator", "Handling operational workflow and verification"},
            {4, "Convenor", "Regional chapter administration"},
            {5, "Sponsor", "Financial support providers"},
            {6, "beneficiary", "Scholarship recipients"},
            {7, "Management", "Strategic oversight and reporting"},
            {8, "Office Coordinator", "Office operations: payment config, RCC centers, courses, sponsor-student mapping and student directory"},
    };

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;
    @Value("${app.bootstrap.admin-name:Administrator}")
    private String adminName;
    @Value("${app.bootstrap.admin-phone:0000000000}")
    private String adminPhone;
    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${app.cors.allowed-origins:}")
    private String siteUrl;

    public BootstrapService(RoleRepository roleRepository, UserRepository userRepository,
                            PasswordEncoder passwordEncoder, EmailService emailService) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        createFirstAdmin();
    }

    private void seedRoles() {
        int added = 0;
        for (Object[] r : BUILT_IN_ROLES) {
            Integer id = (Integer) r[0];
            if (roleRepository.existsById(id)) continue;
            Role role = new Role();
            role.setRoleId(id);
            role.setRoleName((String) r[1]);
            role.setDescription((String) r[2]);
            roleRepository.save(role);
            added++;
        }
        if (added > 0) log.info("Bootstrap: added {} built-in role(s).", added);
    }

    private void createFirstAdmin() {
        if (userRepository.countByRoleId(SUPER_ADMIN) > 0) return;

        String email = !isBlank(adminEmail) ? adminEmail.trim() : (mailUsername == null ? "" : mailUsername.trim());
        if (email.isEmpty()) {
            log.warn("Bootstrap: there is no Super Admin and no ADMIN_EMAIL / MAIL_USERNAME is set, so none was created.");
            return;
        }
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("Bootstrap: there is no Super Admin, but {} already belongs to another user. Not creating one.", email);
            return;
        }

        String password = PasswordPolicy.temporaryPassword();
        User admin = new User();
        admin.setUserId(nextUserId());
        admin.setName(isBlank(adminName) ? "Administrator" : adminName.trim());
        admin.setEmail(email);
        String phone = isBlank(adminPhone) ? "0000000000" : adminPhone.trim();
        admin.setPhone(userRepository.findByPhone(phone).isPresent() ? admin.getUserId() : phone);
        admin.setSex("M");
        admin.setRoleId(SUPER_ADMIN);
        admin.setStatus("Active");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setMustChangePassword(true);
        userRepository.save(admin);

        log.warn("Bootstrap: created the first Super Admin. Sign in with e-mail {} and temporary password {} "
                + "(you will be asked to choose a new one).", email, password);
        emailService.sendWithSecret(email, "Your Rahbar administrator account",
                "An administrator account was created for you on Rahbar"
                        + (isBlank(siteUrl) ? "" : " (" + siteUrl.split(",")[0].trim() + ")") + ".\n\n"
                        + "E-mail: " + email + "\nTemporary password: " + password + "\n\n"
                        + "After signing in you will receive a one-time code by e-mail, and then you must choose a new password.", password);
    }

    private String nextUserId() {
        long max = userRepository.findAllUserIds().stream()
                .filter(id -> id != null && id.matches("\\d{1,15}"))
                .mapToLong(Long::parseLong).max().orElse(1000);
        return String.valueOf(max + 1);
    }

    /** 12 letters and digits, always containing both (meets PasswordPolicy). */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
