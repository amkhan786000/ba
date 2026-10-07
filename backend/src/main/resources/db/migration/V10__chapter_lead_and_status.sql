-- Chapters get a lead (name, phone, email) and an active flag. Existing chapters become active.
-- The new backend also adds these columns itself on start (existing chapters active); either order is fine.
-- Idempotent. Works on MySQL 5.7+/8 and MariaDB.
--
-- Run once (after V7__chapters.sql):
--   mysql -u <user> -p rahbar < V10__chapter_lead_and_status.sql

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

CALL rahbar_add_column('chapters', 'lead_name', 'VARCHAR(100) NULL');
CALL rahbar_add_column('chapters', 'lead_phone', 'VARCHAR(30) NULL');
CALL rahbar_add_column('chapters', 'lead_email', 'VARCHAR(150) NULL');
CALL rahbar_add_column('chapters', 'active', 'BIT(1) NOT NULL DEFAULT 1');
DROP PROCEDURE IF EXISTS rahbar_add_column;

-- New chapters are active unless switched off.
ALTER TABLE chapters MODIFY active BIT(1) NOT NULL DEFAULT 1;
