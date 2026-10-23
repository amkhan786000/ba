-- 1. Students' reference numbers (Students > Reference Numbers): M001-2026 for a Govt college, PVT001-2026 otherwise.
-- 2. What kind of student a sponsor wants: college type, fathers' professions and a family income range.
-- 3. Sponsorship applications: a sponsor's pledge (how many students, with those preferences) for an application
--    period, made by the sponsor or by the office on their behalf (Applications > Apply for Sponsorship).
-- Re-runnable: columns, index and table are only added when missing.
DROP PROCEDURE IF EXISTS rahbar_add_column;
DROP PROCEDURE IF EXISTS rahbar_add_unique;
DELIMITER //
CREATE PROCEDURE rahbar_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
CREATE PROCEDURE rahbar_add_unique(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(200))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = tbl AND index_name = idx) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD UNIQUE KEY `', idx, '` (', cols, ')');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

CALL rahbar_add_column('users', 'reference_no', 'VARCHAR(20) NULL');
CALL rahbar_add_unique('users', 'uk_users_reference_no', 'reference_no');

CALL rahbar_add_column('users', 'pref_college_type', 'VARCHAR(10) NULL');
CALL rahbar_add_column('users', 'pref_father_professions', 'TEXT NULL');
CALL rahbar_add_column('users', 'pref_income_min', 'DECIMAL(12,2) NULL');
CALL rahbar_add_column('users', 'pref_income_max', 'DECIMAL(12,2) NULL');

DROP PROCEDURE IF EXISTS rahbar_add_column;
DROP PROCEDURE IF EXISTS rahbar_add_unique;

CREATE TABLE IF NOT EXISTS sponsorship_applications (
  application_id BIGINT NOT NULL AUTO_INCREMENT,
  period_id BIGINT NOT NULL,
  sponsor_id BIGINT NOT NULL,
  students_wanted INT NOT NULL,
  college_type VARCHAR(10) NULL,
  father_professions TEXT NULL,
  income_min DECIMAL(12,2) NULL,
  income_max DECIMAL(12,2) NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (application_id),
  UNIQUE KEY uk_sponsorship_application (period_id, sponsor_id),
  KEY idx_sponsorship_application_sponsor (sponsor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Who may apply (Section SPONSOR_APPLY). VIEW: apply for oneself (a sponsor). EDIT: apply on behalf of any sponsor
-- (chapter leads: their chapter's) and see the applications. The Super Admin always may; changeable in Roles.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'SPONSOR_APPLY:VIEW', 'SPONSOR_APPLY:EDIT')
 WHERE (role_id IN (2, 8) OR LOWER(role_name) = 'chapter lead')
   AND FIND_IN_SET('SPONSOR_APPLY:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'SPONSOR_APPLY:VIEW')
 WHERE role_id = 5 AND FIND_IN_SET('SPONSOR_APPLY:VIEW', COALESCE(permissions, '')) = 0;
