-- How a sponsor was introduced (with "c/o", who brought them): the medium the introduction came through
-- (WHATSAPP, MESSAGE, EMAIL or OTHER, with a few words for other) and the date it was communicated. Set on Edit
-- Sponsor; all optional. Re-runnable.
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
CALL rahbar_add_column('users', 'introduced_via', 'VARCHAR(20) NULL');
CALL rahbar_add_column('users', 'introduced_via_other', 'VARCHAR(100) NULL');
CALL rahbar_add_column('users', 'introduced_on', 'DATE NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
