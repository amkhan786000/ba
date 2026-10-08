-- Sign-in protection and emailed password-reset codes.
--   users.failed_attempts / locked_until: 5 wrong passwords or OTPs lock the account for 15 minutes.
--   password_reset_codes: "Forgot password" emails a 6-digit code (stored hashed), valid 15 minutes, 5 tries.

DROP PROCEDURE IF EXISTS rahbar_add_column;
DELIMITER //
CREATE PROCEDURE rahbar_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;
CALL rahbar_add_column('users', 'failed_attempts', 'INT NOT NULL DEFAULT 0');
CALL rahbar_add_column('users', 'locked_until', 'DATETIME(6) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

CREATE TABLE IF NOT EXISTS password_reset_codes (
  reset_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  used BIT(1) NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (reset_id),
  KEY idx_reset_user (user_id),
  CONSTRAINT fk_password_reset_codes_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_password_reset_codes_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_password_reset_codes_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
