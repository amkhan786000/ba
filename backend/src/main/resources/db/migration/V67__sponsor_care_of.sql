-- Sponsors brought in by someone else: "c/o" (care of) who brought them, e.g. "Abdul Rahman c/o Imran Khan".
-- Optional; set in Edit Sponsor or by the Manage Users bulk upload. Re-runnable.
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
CALL rahbar_add_column('users', 'care_of', 'VARCHAR(150) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
