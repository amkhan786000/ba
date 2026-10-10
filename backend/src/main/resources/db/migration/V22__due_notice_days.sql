-- Payment Config: how many days before its due date an installment shows as "Due" (and the sponsor gets the
-- "due soon" reminder). After the due date it shows as "Overdue". Existing years: 30 days.
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
CALL rahbar_add_column('payment_schedules', 'due_notice_days', 'INT NOT NULL DEFAULT 30');
DROP PROCEDURE IF EXISTS rahbar_add_column;
