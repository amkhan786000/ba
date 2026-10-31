-- Institutions get a Code: a copy of today's name, kept as it is while the name is edited later (e.g. Code "DRIT",
-- name "Dr. Rajendra Institute of Technology"). Every page keeps showing the name; the code shows in the Institution
-- List and on the institution form. Re-runnable.
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
CALL rahbar_add_column('institutions', 'institution_code', 'VARCHAR(255) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

UPDATE institutions SET institution_code = institution_name WHERE institution_code IS NULL;
