-- Anonymous sponsor: a sponsor (often someone passing on money from donors in several chapters) whose identity only
-- the Super Admin and the sponsor themselves see. Everyone else (students, chapter users, office staff) sees
-- "Anonymous sponsor" / "A sponsor from <the student's chapter>". Set by the Super Admin in Users > Edit.
-- Re-runnable: adds the column only when it is missing.

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

CALL rahbar_add_column('users', 'anonymous_sponsor', 'BIT NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS rahbar_add_column;
