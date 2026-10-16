-- Payment Config by college type: a session year can have one record for government colleges, one for private
-- colleges and/or one for any college ("ANY"). Existing records become "ANY" (they applied to everyone).
-- The student's BA Details get a college type that picks the record: GOVT / PRIVATE / ANY ("doesn't matter");
-- NULL = not set yet (treated like ANY, and listed in Data Quality).
--
-- Written for the live database (upgraded from before Flyway, so the unique key on year may have another name than
-- uq_ps_year): every step checks first and can run again.

DROP PROCEDURE IF EXISTS rahbar_add_column;
DROP PROCEDURE IF EXISTS rahbar_drop_unique_on;
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
-- Drops every unique index of the table that is on exactly this one column.
CREATE PROCEDURE rahbar_drop_unique_on(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
  DECLARE idx VARCHAR(64);
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT s.index_name FROM information_schema.statistics s
     WHERE s.table_schema = DATABASE() AND s.table_name = tbl AND s.non_unique = 0 AND s.index_name <> 'PRIMARY'
     GROUP BY s.index_name
    HAVING COUNT(*) = 1 AND MAX(s.column_name) = col;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO idx;
    IF done = 1 THEN LEAVE read_loop; END IF;
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` DROP INDEX `', idx, '`');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
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

CALL rahbar_add_column('payment_schedules', 'college_type', "VARCHAR(10) NOT NULL DEFAULT 'ANY'");
CALL rahbar_drop_unique_on('payment_schedules', 'year');
CALL rahbar_add_unique('payment_schedules', 'uq_ps_year_college_type', 'year, college_type');
CALL rahbar_add_column('users', 'college_type', 'VARCHAR(10) NULL');

-- Every student there is now (main role or an extra role 6) starts as "Doesn't matter", so the year's Payment Config
-- (Any college, or its only one) keeps applying as before. Students added later start as "not set" and show in
-- Data Quality until Govt / Private / Doesn't matter is chosen. Only empty values are filled: re-runnable.
UPDATE users SET college_type = 'ANY'
 WHERE college_type IS NULL
   AND (role_id = 6 OR id IN (SELECT r.user_id FROM user_additional_roles r WHERE r.role_id = 6));

DROP PROCEDURE IF EXISTS rahbar_add_column;
DROP PROCEDURE IF EXISTS rahbar_drop_unique_on;
DROP PROCEDURE IF EXISTS rahbar_add_unique;
