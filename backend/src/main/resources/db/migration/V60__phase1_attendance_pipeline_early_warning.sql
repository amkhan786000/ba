-- Phase 1:
-- 1. Coaching attendance: a daily register per center and class (Present / Absent / Leave).
-- 2. Coaching -> scholarship pipeline: a coaching student's email, when (and how) they were invited to apply for
--    the Rahbar scholarship, and the scholarship application they then made.
-- 3. Dropout early warning: follow-up calls / visits recorded against a scholarship or coaching student.
-- Re-runnable.
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
CALL rahbar_add_column('coaching_students', 'email', 'VARCHAR(150) NULL');
CALL rahbar_add_column('coaching_students', 'invited_at', 'DATETIME(6) NULL');
CALL rahbar_add_column('coaching_students', 'invited_by', 'BIGINT NULL');
CALL rahbar_add_column('coaching_students', 'invite_method', 'VARCHAR(10) NULL');
CALL rahbar_add_column('coaching_students', 'application_id', 'BIGINT NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

CREATE TABLE IF NOT EXISTS coaching_attendance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rcc_center_id BIGINT NOT NULL,
  class_level INT NOT NULL,
  att_date DATE NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(1) NOT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coaching_attendance (student_id, att_date),
  KEY idx_coaching_attendance_class (rcc_center_id, class_level, att_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_followups (
  id BIGINT NOT NULL AUTO_INCREMENT,
  kind VARCHAR(10) NOT NULL,
  student_id BIGINT NOT NULL,
  followup_date DATE NOT NULL,
  method VARCHAR(10) NOT NULL,
  outcome VARCHAR(10) NOT NULL,
  note VARCHAR(1000) NULL,
  next_date DATE NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_student_followups_student (kind, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Coaching attendance: whoever sees coaching students sees it; whoever manages them marks it; Coaching Teachers mark it too.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'COACHING_ATTENDANCE:VIEW')
 WHERE (FIND_IN_SET('RCC_STUDENTS:VIEW', COALESCE(permissions, '')) > 0 OR FIND_IN_SET('RCC_STUDENTS:EDIT', COALESCE(permissions, '')) > 0)
   AND FIND_IN_SET('COACHING_ATTENDANCE:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'COACHING_ATTENDANCE:EDIT')
 WHERE (FIND_IN_SET('RCC_STUDENTS:EDIT', COALESCE(permissions, '')) > 0 OR LOWER(role_name) = 'coaching teacher')
   AND FIND_IN_SET('COACHING_ATTENDANCE:EDIT', COALESCE(permissions, '')) = 0;

-- Early warning: the office, chapter leads (their chapter) and coaching center coordinators (their center) see the
-- list and record follow-ups. The Super Admin always may; changeable in Roles.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'EARLY_WARNING:VIEW', 'EARLY_WARNING:EDIT')
 WHERE (role_id IN (2, 8) OR LOWER(role_name) IN ('chapter lead', 'rcc coordinator'))
   AND FIND_IN_SET('EARLY_WARNING:VIEW', COALESCE(permissions, '')) = 0;
