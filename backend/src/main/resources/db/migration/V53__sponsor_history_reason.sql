-- Why a student's sponsorship ended (e.g. "Sponsor declined to continue"), typed when the office changes or removes
-- the student's sponsor; shown in the student's sponsor history. Re-runnable: adds the column only when missing.
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
CALL rahbar_add_column('sponsor_mapping_history', 'end_reason', 'VARCHAR(300) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
