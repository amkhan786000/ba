package com.rahbar.service;

import com.rahbar.config.LegacyCompatiblePasswordEncoder;
import com.rahbar.dto.*;
import com.rahbar.entity.Otp;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.OtpRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Login (+ OTP step-up), self-registration and password reset. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final com.rahbar.security.RahbarUserDetailsService userDetailsService;
    private final com.rahbar.repository.PasswordResetCodeRepository resetCodeRepository;

    /** Wrong passwords / OTPs (or guesses of one reset code) allowed before locking / voiding. */
    public static final int MAX_ATTEMPTS = 5;
    static final int LOCK_MINUTES = 15;
    static final int RESET_CODE_MINUTES = 15;
    /** At most this many reset codes per user per hour (stops email flooding). */
    static final int RESET_CODES_PER_HOUR = 3;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    /** Local testing only (LOG_OTP=true): print the OTP in the backend log so sign-in works without SMTP. */
    @org.springframework.beans.factory.annotation.Value("${app.mail.log-otp:false}")
    private boolean logOtp;

    public AuthService(UserRepository userRepository, OtpRepository otpRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       EmailService emailService, ActivityLogService activityLogService,
                       NotificationService notificationService,
                       com.rahbar.security.RahbarUserDetailsService userDetailsService,
                       com.rahbar.repository.PasswordResetCodeRepository resetCodeRepository) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
        this.userDetailsService = userDetailsService;
        this.resetCodeRepository = resetCodeRepository;
    }

    public AuthResponse login(LoginRequest req, String ip) {
        Optional<User> userOpt = "phone".equalsIgnoreCase(req.getLoginMethod())
                ? userRepository.findByPhone(req.getIdentifier())
                : userRepository.findByEmail(req.getIdentifier());

        if (userOpt.isEmpty()) {
            activityLogService.record(null, "Failed sign-in: unknown " + req.getLoginMethod() + " " + req.getIdentifier(),
                    "POST", "/api/auth/login", 401, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid " + req.getLoginMethod() + " or password");
        }
        User user = userOpt.get();
        requireNotLocked(user);

        if ("Inactive".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is inactive. Please contact the administrator.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            activityLogService.record(user, "Failed sign-in: wrong password", "POST", "/api/auth/login", 401, ip);
            registerFailure(user, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid " + req.getLoginMethod() + " or password");
        }
        clearFailures(user);

        // Self-heal legacy plain-text passwords into BCrypt on successful login.
        if (passwordEncoder instanceof LegacyCompatiblePasswordEncoder legacy
                && legacy.isLegacyPlainText(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            userRepository.save(user);
        }

        // Anyone still using the default password given to bulk-created accounts must change it.
        if (PasswordPolicy.DEFAULT_PASSWORD.equals(req.getPassword()) && !Boolean.TRUE.equals(user.getMustChangePassword())) {
            user.setMustChangePassword(true);
            userRepository.save(user);
        }

        if ("registered".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is not yet activated. Please wait for approval.");
        }
        if ("recognised".equalsIgnoreCase(user.getStatus())) {
            activityLogService.record(user, "Signed in", "POST", "/api/auth/login", 200, ip);
            return issueToken(user); // original redirected straight to public_application
        }

        // Issue an OTP step-up, same as the Flask flow.
        String otpCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        Otp otp = new Otp();
        otp.setUserId(user.getId());
        otp.setOtp(otpCode);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setStatus(0);
        otpRepository.save(otp);

        emailService.send(user.getEmail(), "Your Login OTP",
                "Your OTP for login is " + otpCode + ". It is valid for 5 minutes.");
        if (logOtp) log.warn("LOG_OTP is on: sign-in OTP for {} is {}", user.getUserId(), otpCode);

        return AuthResponse.otpRequired(user.getId(), "An OTP has been sent to your email. Please verify.");
    }

    public AuthResponse verifyOtp(OtpVerifyRequest req, String ip) {
        User user = (req.getId() == null ? java.util.Optional.<User>empty() : userRepository.findById(req.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP. Please try again."));
        requireNotLocked(user);

        // No master code any more: every OTP sign-in needs the code that was emailed.
        Otp otp = otpRepository.findByUserIdAndOtpAndStatus(user.getId(), req.getOtp(), 0).orElse(null);
        if (otp == null) {
            activityLogService.record(user, "Failed sign-in: wrong OTP", "POST", "/api/auth/verify-otp", 400, ip);
            registerFailure(user, ip);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP. Please try again.");
        }
        if (otp.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP has expired. Please log in again.");
        }
        otp.setStatus(1);
        otpRepository.save(otp);
        clearFailures(user);

        activityLogService.record(user, "Signed in", "POST", "/api/auth/verify-otp", 200, ip);
        return issueToken(user);
    }

    /** 423 while the account is locked after too many wrong passwords / OTPs. */
    private void requireNotLocked(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = Math.max(1, java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.LOCKED, "Too many failed attempts. Try again in " + minutes + " minute(s), "
                    + "or reset your password with \"Forgot password\".");
        }
    }

    /** One more wrong password / OTP; the MAX_ATTEMPTS-th locks the account for LOCK_MINUTES. */
    private void registerFailure(User user, String ip) {
        int failures = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        if (failures >= MAX_ATTEMPTS) {
            user.setFailedAttempts(0);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            activityLogService.record(user, "Account locked for " + LOCK_MINUTES + " minutes after " + MAX_ATTEMPTS
                    + " failed sign-in attempts", "POST", "/api/auth/login", 423, ip);
        } else {
            user.setFailedAttempts(failures);
        }
        userRepository.save(user);
    }

    private void clearFailures(User user) {
        if ((user.getFailedAttempts() != null && user.getFailedAttempts() > 0) || user.getLockedUntil() != null) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getId(), Map.of(
                "roleId", user.getRoleId(),
                "name", user.getName(),
                "status", user.getStatus()
        ));
        AuthResponse response = AuthResponse.success(token, user.getId(), user.getUserId(), user.getName(), user.getRoleId(), user.getStatus());
        response.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()));
        var principal = userDetailsService.principalFor(user);
        response.setPermissions(principal.getPermissions());
        response.setScope(principal.getScope());
        return response;
    }

    public void register(RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email address already exists");
        }
        // Self-registration is only for sponsors (5) and students (6); staff roles are created by an admin.
        if (req.getRole() == null || (req.getRole() != 5 && req.getRole() != 6)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can only register as a Sponsor or a Student.");
        }
        User user = new User();
        // users.id is generated by the database; user_id is the human-facing code
        // (real student/sponsor codes come from the admin-side bulk import flows).
        user.setUserId("REG-" + System.currentTimeMillis());
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setSex(req.getSex());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getContact());
        user.setRoleId(req.getRole());
        user.setStatus("registered");
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    /**
     * Step 1 of "Forgot password": emails a 6-digit code (valid RESET_CODE_MINUTES, one use). Says nothing about
     * whether the email exists, so the page can't be used to find out who has an account.
     */
    public void requestPasswordReset(String email, String ip) {
        Optional<User> found = email == null || email.isBlank() ? Optional.empty() : userRepository.findByEmail(email.trim());
        if (found.isEmpty() || "Inactive".equalsIgnoreCase(found.get().getStatus())) {
            activityLogService.record(null, "Password reset requested for an unknown or inactive email", "POST",
                    "/api/auth/forgot-password", 200, ip);
            return;
        }
        User user = found.get();
        if (resetCodeRepository.countByUserIdAndCreatedAtAfter(user.getId(), LocalDateTime.now().minusHours(1)) >= RESET_CODES_PER_HOUR) {
            activityLogService.record(user, "Password reset requested too often (no code sent)", "POST", "/api/auth/forgot-password", 200, ip);
            return;
        }
        // Only the newest code works.
        List<com.rahbar.entity.PasswordResetCode> open = resetCodeRepository.findByUserIdAndUsedFalse(user.getId());
        open.forEach(c -> c.setUsed(true));
        resetCodeRepository.saveAll(open);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        com.rahbar.entity.PasswordResetCode reset = new com.rahbar.entity.PasswordResetCode();
        reset.setUserId(user.getId());
        reset.setCodeHash(passwordEncoder.encode(code));
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_CODE_MINUTES));
        resetCodeRepository.save(reset);

        emailService.send(user.getEmail(), "Rahbar: your password reset code",
                "Dear " + user.getName() + ",\n\nYour code to reset your Rahbar password is: " + code
                        + "\n\nIt is valid for " + RESET_CODE_MINUTES + " minutes. If you didn't ask for this, ignore this email;"
                        + " your password stays the same.\n\nRegards,\nRahbar - Bihar Anjuman");
        if (logOtp) log.warn("LOG_OTP is on: password reset code for {} is {}", user.getUserId(), code);
        activityLogService.record(user, "Password reset code sent", "POST", "/api/auth/forgot-password", 200, ip);
    }

    /** Step 2 of "Forgot password": the emailed code plus the new password. */
    public void resetPassword(ResetPasswordRequest req, String ip) {
        ApiException invalid = new ApiException(HttpStatus.BAD_REQUEST, "The code is wrong or has expired. Ask for a new code.");
        User user = (req.getEmail() == null ? Optional.<User>empty() : userRepository.findByEmail(req.getEmail().trim()))
                .orElseThrow(() -> invalid);
        com.rahbar.entity.PasswordResetCode reset = resetCodeRepository.findFirstByUserIdAndUsedFalseOrderByResetIdDesc(user.getId())
                .filter(c -> c.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> invalid);
        String code = req.getCode() == null ? "" : req.getCode().trim();
        if (!passwordEncoder.matches(code, reset.getCodeHash())) {
            reset.setAttempts(reset.getAttempts() + 1);
            if (reset.getAttempts() >= MAX_ATTEMPTS) reset.setUsed(true); // too many guesses: this code is void
            resetCodeRepository.save(reset);
            activityLogService.record(user, "Password reset: wrong code", "POST", "/api/auth/reset-password", 400, ip);
            throw invalid;
        }
        PasswordPolicy.check(req.getNewPassword(), req.getConfirmPassword());
        reset.setUsed(true);
        resetCodeRepository.save(reset);
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null); // proving access to the mailbox also lifts a sign-in lock
        userRepository.save(user);
        activityLogService.record(user, "Reset a forgotten password", "POST", "/api/auth/reset-password", 200, ip);
        // Same notice as a password change from the profile: in-app notification plus email.
        notificationService.notify(user.getId(), "Password changed",
                "Your Rahbar password was reset using \"Forgot password\". If this wasn't you, contact the administrator straight away.",
                NotificationService.ACCOUNT, null, true);
    }

    /** The user a bearer token belongs to. */
    public User userForToken(String token) {
        Long id = Long.valueOf(jwtService.extractUserId(token));
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
