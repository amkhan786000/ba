package com.rahbar.service;

import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Rules for passwords people choose themselves (change password, reset password). */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    /** Initial password given to accounts created by an admin or a bulk upload; it must be changed at first sign-in. */
    public static final String DEFAULT_PASSWORD = "hello";

    public static void check(String password, String confirm) {
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must contain at least one letter and one number.");
        }
        if (password.equalsIgnoreCase(DEFAULT_PASSWORD)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a password other than the default one.");
        }
        if (confirm != null && !password.equals(confirm)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
    }
}
