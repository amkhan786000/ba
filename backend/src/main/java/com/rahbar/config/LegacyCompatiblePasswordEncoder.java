package com.rahbar.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The original Flask app stored passwords as PLAIN TEXT in users.password_hash
 * and compared them with a raw string equality check (see routes/auth.py).
 * That is a serious pre-existing security issue.
 *
 * To migrate without locking out every existing account on day one, this
 * encoder accepts either a BCrypt hash (new/changed passwords) or a legacy
 * plain-text match (old rows). AuthController re-hashes a user's password
 * with BCrypt the moment they successfully log in with a legacy plain-text
 * value, so accounts self-heal over time.
 *
 * TODO (security): once you've confirmed every active user has logged in
 * at least once post-migration, drop the plain-text fallback below.
 */
public class LegacyCompatiblePasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) return false;
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            return bcrypt.matches(rawPassword, encodedPassword);
        }
        // Legacy plain-text row carried over from the Flask app.
        return encodedPassword.equals(rawPassword == null ? null : rawPassword.toString());
    }

    public boolean isLegacyPlainText(String storedPassword) {
        return storedPassword != null
                && !storedPassword.startsWith("$2a$")
                && !storedPassword.startsWith("$2b$")
                && !storedPassword.startsWith("$2y$");
    }
}
