-- Anonymous sponsors are real sponsors: a sponsorship on behalf of an anonymous sponsor can point to that sponsor's
-- account (donor_user_id). The sponsor who mapped the student stays the sponsor of record and the contact; only the
-- Super Admin and the Application Administrator see who the anonymous sponsor is. Older on-behalf sponsorships
-- (chapter + label, no account) keep working and can be linked later. Re-runnable.
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
CALL rahbar_add_column('grantor_grantees', 'donor_user_id', 'BIGINT NULL');
CALL rahbar_add_column('sponsor_mapping_history', 'donor_user_id', 'BIGINT NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

-- foreign keys to users(id), once
SET @fk := (SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'grantor_grantees' AND constraint_name = 'fk_grantor_grantees_donor_user');
SET @ddl := IF(@fk = 0, 'ALTER TABLE grantor_grantees ADD CONSTRAINT fk_grantor_grantees_donor_user FOREIGN KEY (donor_user_id) REFERENCES users(id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @fk := (SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_schema = DATABASE() AND table_name = 'sponsor_mapping_history' AND constraint_name = 'fk_sponsor_mapping_history_donor_user');
SET @ddl := IF(@fk = 0, 'ALTER TABLE sponsor_mapping_history ADD CONSTRAINT fk_sponsor_mapping_history_donor_user FOREIGN KEY (donor_user_id) REFERENCES users(id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
