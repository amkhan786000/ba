-- Platform features: forced password change, progress review, richer notifications,
-- application documents + interview scheduling, and the activity log.
--
-- Idempotent: every column / table / index is only added when missing, so it is safe to re-run.
-- Works on MySQL 5.7+/8 and MariaDB.
--
-- Run once (Flyway is not on the classpath, so this does not run automatically), after a backup:
--   mysqldump -u <user> -p rahbar > rahbar-before-v6.sql
--   mysql -u <user> -p rahbar < V6__platform_features.sql

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

-- 1. Users must pick their own password after an admin / bulk upload creates the account.
CALL rahbar_add_column('users', 'must_change_password', 'TINYINT(1) NOT NULL DEFAULT 0');

-- 2. Progress review (Pending / Approved / Rejected) by the sponsor, convenor or admin.
CALL rahbar_add_column('student_progress', 'review_status', "VARCHAR(20) NOT NULL DEFAULT 'Pending'");
CALL rahbar_add_column('student_progress', 'review_comment', 'TEXT NULL');
CALL rahbar_add_column('student_progress', 'reviewed_by', 'VARCHAR(50) NULL');
CALL rahbar_add_column('student_progress', 'reviewed_at', 'DATETIME NULL');

-- 3. In-app notifications: a title, a category for the icon, a link to open, and a de-duplication key
--    (payment reminders use it so the same reminder is never sent twice).
CALL rahbar_add_column('notifications', 'title', 'VARCHAR(255) NULL');
CALL rahbar_add_column('notifications', 'category', 'VARCHAR(50) NULL');
CALL rahbar_add_column('notifications', 'link', 'VARCHAR(255) NULL');
CALL rahbar_add_column('notifications', 'ref_key', 'VARCHAR(191) NULL');

-- 4. Interview scheduling on an application.
CALL rahbar_add_column('grantee_details', 'interview_at', 'DATETIME NULL');
CALL rahbar_add_column('grantee_details', 'interview_venue', 'VARCHAR(255) NULL');

DROP PROCEDURE IF EXISTS rahbar_add_column;

-- One notification per de-duplication key (NULL keys are allowed many times).
DROP PROCEDURE IF EXISTS rahbar_add_ref_key_index;
DELIMITER //
CREATE PROCEDURE rahbar_add_ref_key_index()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'uq_notifications_ref_key') THEN
    ALTER TABLE notifications ADD UNIQUE KEY uq_notifications_ref_key (ref_key);
  END IF;
END //
DELIMITER ;
CALL rahbar_add_ref_key_index();
DROP PROCEDURE IF EXISTS rahbar_add_ref_key_index;

-- 5. Documents uploaded with (or after) a scholarship application.
CREATE TABLE IF NOT EXISTS application_documents (
  document_id BIGINT NOT NULL AUTO_INCREMENT,
  grantee_detail_id BIGINT NOT NULL,
  doc_type VARCHAR(50) NULL,
  file_name VARCHAR(255) NULL,
  file_path VARCHAR(255) NOT NULL,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (document_id),
  KEY idx_appdoc_application (grantee_detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Activity log: who did what, and when (every change made through the API, plus sign-ins).
CREATE TABLE IF NOT EXISTS activity_log (
  log_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(50) NULL,
  user_name VARCHAR(255) NULL,
  role_id INT NULL,
  action VARCHAR(255) NOT NULL,
  method VARCHAR(10) NULL,
  path VARCHAR(255) NULL,
  status_code INT NULL,
  ip_address VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_activity_created (created_at),
  KEY idx_activity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
