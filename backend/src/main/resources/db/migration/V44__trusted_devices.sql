-- "Remember this device": after the emailed sign-in code, a user may let a browser skip the code (the password is
-- still asked) for a number of days set in Administration > Application Settings. Only a SHA-256 hash of the
-- browser's random token is kept here; the token itself lives in an HttpOnly cookie on that browser.
-- Changing or resetting the password forgets all of a user's devices.
CREATE TABLE IF NOT EXISTS trusted_devices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  device_name VARCHAR(120) NULL,             -- e.g. "Chrome on Windows", from the browser's User-Agent
  ip_address VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  last_used_at DATETIME(6) NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_trusted_devices_token (token_hash),
  KEY idx_trusted_devices_user (user_id),
  CONSTRAINT fk_trusted_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
