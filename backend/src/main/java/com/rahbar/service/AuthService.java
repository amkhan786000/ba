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

    private static final SecureRandom RANDOM = new SecureRandom();

    public AuthService(UserRepository userRepository, OtpRepository otpRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    public AuthResponse login(LoginRequest req) {
        Optional<User> userOpt = "phone".equalsIgnoreCase(req.getLoginMethod())
                ? userRepository.findByPhone(req.getIdentifier())
                : userRepository.findByEmail(req.getIdentifier());

        User user = userOpt.orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "Invalid " + req.getLoginMethod() + " or password"));

        if ("Inactive".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is inactive. Please contact the administrator.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid " + req.getLoginMethod() + " or password");
        }

        // Self-heal legacy plain-text passwords into BCrypt on successful login.
        if (passwordEncoder instanceof LegacyCompatiblePasswordEncoder legacy
                && legacy.isLegacyPlainText(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            userRepository.save(user);
        }

        if ("registered".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is not yet activated. Please wait for approval.");
        }
        if ("recognised".equalsIgnoreCase(user.getStatus())) {
            return issueToken(user); // original redirected straight to public_application
        }

        // Issue an OTP step-up, same as the Flask flow.
        String otpCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        Otp otp = new Otp();
        otp.setUserId(user.getUserId());
        otp.setOtp(otpCode);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setStatus(0);
        otpRepository.save(otp);

        emailService.send(user.getEmail(), "Your Login OTP",
                "Your OTP for login is " + otpCode + ". It is valid for 5 minutes.");

        return AuthResponse.otpRequired(user.getUserId(), "An OTP has been sent to your email. Please verify.");
    }

    public AuthResponse verifyOtp(OtpVerifyRequest req) {
        // '477030' preserved as a master/testing bypass code, exactly like the original code.
        boolean masterBypass = "477030".equals(req.getOtp());

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP. Please try again."));

        if (!masterBypass) {
            Otp otp = otpRepository.findByUserIdAndOtpAndStatus(req.getUserId(), req.getOtp(), 0)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP. Please try again."));
            if (otp.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OTP has expired. Please log in again.");
            }
            otp.setStatus(1);
            otpRepository.save(otp);
        }

        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getUserId(), Map.of(
                "roleId", user.getRoleId(),
                "name", user.getName(),
                "status", user.getStatus()
        ));
        return AuthResponse.success(token, user.getUserId(), user.getName(), user.getRoleId(), user.getStatus());
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
        // NOTE: the original schema's user_id is not auto-increment for 'users';
        // real student/sponsor IDs come from the admin-side bulk import flows.
        // For public self-registration we generate a simple unique id.
        user.setUserId("REG-" + System.currentTimeMillis());
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setSex(req.getSex());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getContact());
        user.setRoleId(req.getRole());
        user.setStatus("registered");
        userRepository.save(user);
    }

    public void resetPassword(ResetPasswordRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found."));
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    /** The user a bearer token belongs to. */
    public User userForToken(String token) {
        String userId = jwtService.extractUserId(token);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
