-- A student who graduates (early or on time) keeps her unpaid installments, marked "Not needed": they are never
-- due, overdue or reminded, and the payment history still shows the whole plan. Re-runnable.
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
CALL rahbar_add_column('payment_installments', 'not_needed', 'BIT NOT NULL DEFAULT 0');
DROP PROCEDURE IF EXISTS rahbar_add_column;
