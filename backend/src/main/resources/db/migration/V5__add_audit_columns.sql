-- Audit columns on every table:
--   created_by  VARCHAR(50)  user_id of the creator (NULL for public actions such as self-registration)
--   updated_by  VARCHAR(50)  user_id of the last person to change the row
--   created_at  DATETIME     set once on insert (DEFAULT CURRENT_TIMESTAMP); the app never updates it
--   updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
--
-- Loops over every table in the current database and adds only the columns a table is missing, so it is
-- safe on tables that already have some of them (existing columns keep their current type and data).
-- Existing rows get NULL created_by / updated_by. Works on MySQL 5.7+/8 and MariaDB.
--
-- Run once (Flyway is not on the classpath, so this does not run automatically), after a backup:
--   mysqldump -u <user> -p rahbar > rahbar-before-v5.sql
--   mysql -u <user> -p rahbar < V5__add_audit_columns.sql

DROP PROCEDURE IF EXISTS rahbar_add_audit_columns;

DELIMITER //
CREATE PROCEDURE rahbar_add_audit_columns()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE tbl VARCHAR(64);
  DECLARE cur CURSOR FOR
    SELECT table_name FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  table_loop: LOOP
    FETCH cur INTO tbl;
    IF done THEN LEAVE table_loop; END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = 'created_by') THEN
      SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN created_by VARCHAR(50) NULL');
      PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = 'updated_by') THEN
      SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN updated_by VARCHAR(50) NULL');
      PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = 'created_at') THEN
      SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP');
      PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = 'updated_at') THEN
      SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
      PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE cur;
END //
DELIMITER ;

CALL rahbar_add_audit_columns();
DROP PROCEDURE rahbar_add_audit_columns;
