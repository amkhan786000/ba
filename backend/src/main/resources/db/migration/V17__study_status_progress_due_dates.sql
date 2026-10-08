-- Student study status (separate from the account's Active / Inactive), progress-report due dates set by the
-- office, and the permissions for the new admin screens.

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
-- STUDYING, ON_HOLD, GRADUATED or DROPPED_OUT (students only; NULL counts as STUDYING).
CALL rahbar_add_column('users', 'study_status', 'VARCHAR(20) NULL');
CALL rahbar_add_column('users', 'study_status_date', 'DATE NULL');
CALL rahbar_add_column('users', 'study_status_note', 'VARCHAR(500) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

UPDATE users SET study_status = 'STUDYING' WHERE role_id = 6 AND study_status IS NULL;

-- Every study-status change, newest last.
CREATE TABLE IF NOT EXISTS student_status_history (
  history_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  effective_date DATE NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (history_id),
  KEY idx_status_history_user (user_id),
  CONSTRAINT fk_student_status_history_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_student_status_history_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_student_status_history_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Dates by which students must upload a progress report (set in Admin > Progress Due Dates).
CREATE TABLE IF NOT EXISTS progress_due_dates (
  due_id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  due_date DATE NOT NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (due_id),
  UNIQUE KEY uq_progress_due_date (due_date),
  CONSTRAINT fk_progress_due_dates_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_progress_due_dates_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- New screens: Progress Due Dates, Chapter Dashboard, Data Quality.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'PROGRESS_DUE_DATES:VIEW', 'PROGRESS_DUE_DATES:EDIT', 'CHAPTER_DASHBOARD:VIEW', 'CHAPTER_DASHBOARD:EDIT',
       'DATA_QUALITY:VIEW', 'DATA_QUALITY:EDIT')
 WHERE role_id IN (1, 2) AND FIND_IN_SET('DATA_QUALITY:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'PROGRESS_DUE_DATES:VIEW', 'PROGRESS_DUE_DATES:EDIT', 'DATA_QUALITY:VIEW')
 WHERE role_id = 8 AND FIND_IN_SET('DATA_QUALITY:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'CHAPTER_DASHBOARD:VIEW')
 WHERE LOWER(role_name) = 'chapter lead' AND FIND_IN_SET('CHAPTER_DASHBOARD:VIEW', COALESCE(permissions, '')) = 0;
