-- Numeric user ids.
--
-- Before: users.user_id (VARCHAR, e.g. 'STU-1001', 'REG-1712345678', '1005') was the primary key, and every
--         other table stored that string (grantee_id, grantor_id, created_by, ...) without a foreign key.
-- After:  users.id BIGINT AUTO_INCREMENT is the primary key. users.user_id stays as a normal, unique column.
--         Every column that points at a user holds users.id (BIGINT) and has a real FOREIGN KEY to users(id):
--           grantee_details.user_id, bank_details.user_id, grantor_grantees.grantor_id / grantee_id,
--           payments.grantor_id / grantee_id, approvals.approver_id, notifications.user_id,
--           chats.sender_id / receiver_id, student_institution_courses.user_id / assigned_by,
--           student_progress.grantee_id / reviewed_by, otp.user_id, activity_log.user_id,
--           and created_by / updated_by on every table.
--
-- Rows pointing at a user that does not exist cannot get a foreign key. They are first copied into
-- an orphans_<table>_<column> table (only created when there are any), then:
--   - required links (a payment's student, a notification's user, ...): the row is deleted;
--   - optional links (created_by / updated_by / reviewed_by / assigned_by, an application's user_id,
--     the activity log's user_id): the value is set to NULL and the row is kept.
--
-- On an existing database: run ONCE, BEFORE starting the new backend version, after a backup:
--   mysqldump -u <user> -p rahbar > rahbar-before-v8.sql
--   mysql -u <user> -p rahbar < V8__numeric_user_id.sql
-- Re-running is safe: it only converts while users.user_id is still the primary key, and only adds missing keys.
-- On a fresh install (tables created by the new backend), run it once after the first start to add the foreign keys.
-- Everyone is signed out by this release (sign-in tokens carried the old string id).
-- Works on MySQL 5.7+/8 and MariaDB.

DROP PROCEDURE IF EXISTS rahbar_v8;
DROP PROCEDURE IF EXISTS rahbar_v8_ref;
DROP PROCEDURE IF EXISTS rahbar_v8_fk;
DELIMITER //
-- Converts one string column that points at users.user_id into a BIGINT holding users.id.
-- orphan_action: 'delete' (remove rows whose user does not exist) or 'null' (clear the value, keep the row).
CREATE PROCEDURE rahbar_v8_ref(IN tbl VARCHAR(64), IN col VARCHAR(64), IN orphan_action VARCHAR(10))
BEGIN
  DECLARE nullable VARCHAR(3);
  DECLARE orphan_table VARCHAR(64);
  DECLARE orphan_where TEXT;

  SELECT is_nullable INTO nullable FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col
     AND data_type IN ('varchar', 'char') LIMIT 1;

  IF nullable IS NOT NULL THEN
    SET orphan_where = CONCAT('t.`', col, '` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users u WHERE ',
        'CONVERT(u.user_id USING utf8mb4) COLLATE utf8mb4_general_ci = ',
        'CONVERT(t.`', col, '` USING utf8mb4) COLLATE utf8mb4_general_ci)');

    -- 1. Back up and remove / clear values that match no user.
    SET @n = 0;
    SET @ddl = CONCAT('SELECT COUNT(*) INTO @n FROM `', tbl, '` t WHERE ', orphan_where);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    IF @n > 0 THEN
      SET orphan_table = LEFT(CONCAT('orphans_', tbl, '_', col), 64);
      SET @ddl = CONCAT('CREATE TABLE IF NOT EXISTS `', orphan_table, '` AS SELECT t.* FROM `', tbl, '` t WHERE ', orphan_where);
      PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
      IF orphan_action = 'delete' OR nullable = 'NO' THEN
        SET @ddl = CONCAT('DELETE t FROM `', tbl, '` t WHERE ', orphan_where);
      ELSE
        SET @ddl = CONCAT('UPDATE `', tbl, '` t SET t.`', col, '` = NULL WHERE ', orphan_where);
      END IF;
      PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    -- 2. Replace each string id with the user's numeric id (one statement, so no value is converted twice).
    SET @ddl = CONCAT('UPDATE `', tbl, '` t JOIN users u ON ',
        'CONVERT(u.user_id USING utf8mb4) COLLATE utf8mb4_general_ci = ',
        'CONVERT(t.`', col, '` USING utf8mb4) COLLATE utf8mb4_general_ci SET t.`', col, '` = u.id');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

    -- 3. Change the type (keys and indexes on the column are kept). The foreign key is added by rahbar_v8_fk.
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` MODIFY `', col, '` BIGINT ', IF(nullable = 'YES', 'NULL', 'NOT NULL'));
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //

-- Adds a FOREIGN KEY to users(id) on a BIGINT column that does not have one yet.
CREATE PROCEDURE rahbar_v8_fk(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col AND data_type = 'bigint')
     AND NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
                     WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col
                       AND referenced_table_name = 'users') THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD CONSTRAINT `', LEFT(CONCAT('fk_', tbl, '_', col), 64),
                      '` FOREIGN KEY (`', col, '`) REFERENCES users(id)');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //

CREATE PROCEDURE rahbar_v8()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE t_name VARCHAR(64);
  DECLARE c_name VARCHAR(64);
  DECLARE audit_cols CURSOR FOR SELECT tbl, col FROM rahbar_v8_audit_cols;
  DECLARE fk_cols CURSOR FOR SELECT tbl, col FROM rahbar_v8_fk_cols;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  -- Only while users.user_id is still the primary key: makes re-running harmless.
  IF EXISTS (SELECT 1 FROM information_schema.key_column_usage
             WHERE table_schema = DATABASE() AND table_name = 'users'
               AND constraint_name = 'PRIMARY' AND column_name = 'user_id') THEN

    -- Foreign keys that still reference users.user_id would block changing the primary key.
    BEGIN
      DECLARE fk_done INT DEFAULT 0;
      DECLARE fk_table VARCHAR(64);
      DECLARE fk_name VARCHAR(64);
      DECLARE old_fks CURSOR FOR
        SELECT table_name, constraint_name FROM information_schema.referential_constraints
         WHERE constraint_schema = DATABASE() AND referenced_table_name = 'users';
      DECLARE CONTINUE HANDLER FOR NOT FOUND SET fk_done = 1;
      OPEN old_fks;
      fk_loop: LOOP
        FETCH old_fks INTO fk_table, fk_name;
        IF fk_done THEN LEAVE fk_loop; END IF;
        SET @ddl = CONCAT('ALTER TABLE `', fk_table, '` DROP FOREIGN KEY `', fk_name, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
      END LOOP;
      CLOSE old_fks;
    END;

    -- A started-too-early new backend may have tried to add users.id: start clean.
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'id') THEN
      ALTER TABLE users DROP COLUMN id;
    END IF;

    -- 1. users: new numeric primary key; the old string id stays as a unique column.
    ALTER TABLE users
      DROP PRIMARY KEY,
      ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
      ADD UNIQUE KEY uq_users_user_id (user_id);

    -- 2. Columns that link rows to a user.
    CALL rahbar_v8_ref('grantee_details', 'user_id', 'null');
    CALL rahbar_v8_ref('bank_details', 'user_id', 'delete');
    CALL rahbar_v8_ref('grantor_grantees', 'grantor_id', 'delete');
    CALL rahbar_v8_ref('grantor_grantees', 'grantee_id', 'delete');
    CALL rahbar_v8_ref('payments', 'grantor_id', 'delete');
    CALL rahbar_v8_ref('payments', 'grantee_id', 'delete');
    CALL rahbar_v8_ref('approvals', 'approver_id', 'delete');
    CALL rahbar_v8_ref('notifications', 'user_id', 'delete');
    CALL rahbar_v8_ref('chats', 'sender_id', 'delete');
    CALL rahbar_v8_ref('chats', 'receiver_id', 'delete');
    CALL rahbar_v8_ref('student_institution_courses', 'user_id', 'delete');
    CALL rahbar_v8_ref('student_institution_courses', 'assigned_by', 'null');
    CALL rahbar_v8_ref('student_progress', 'grantee_id', 'delete');
    CALL rahbar_v8_ref('student_progress', 'reviewed_by', 'null');
    CALL rahbar_v8_ref('otp', 'user_id', 'delete');
    CALL rahbar_v8_ref('activity_log', 'user_id', 'null');

    -- 3. created_by / updated_by on every table (collected first: the loop changes information_schema).
    DROP TEMPORARY TABLE IF EXISTS rahbar_v8_audit_cols;
    CREATE TEMPORARY TABLE rahbar_v8_audit_cols AS
      SELECT table_name AS tbl, column_name AS col FROM information_schema.columns
       WHERE table_schema = DATABASE() AND column_name IN ('created_by', 'updated_by')
         AND data_type IN ('varchar', 'char') AND table_name NOT LIKE 'orphans\_%';
    OPEN audit_cols;
    audit_loop: LOOP
      FETCH audit_cols INTO t_name, c_name;
      IF done THEN LEAVE audit_loop; END IF;
      CALL rahbar_v8_ref(t_name, c_name, 'null');
    END LOOP;
    CLOSE audit_cols;
    DROP TEMPORARY TABLE IF EXISTS rahbar_v8_audit_cols;
  END IF;

  -- 4. Foreign keys to users(id). Also runs on a database the new backend created itself (fresh install),
  --    where the columns are already BIGINT; columns that already have one are skipped.
  DROP TEMPORARY TABLE IF EXISTS rahbar_v8_fk_cols;
  CREATE TEMPORARY TABLE rahbar_v8_fk_cols (tbl VARCHAR(64), col VARCHAR(64));
  INSERT INTO rahbar_v8_fk_cols VALUES
    ('grantee_details', 'user_id'), ('bank_details', 'user_id'),
    ('grantor_grantees', 'grantor_id'), ('grantor_grantees', 'grantee_id'),
    ('payments', 'grantor_id'), ('payments', 'grantee_id'), ('approvals', 'approver_id'),
    ('notifications', 'user_id'), ('chats', 'sender_id'), ('chats', 'receiver_id'),
    ('student_institution_courses', 'user_id'), ('student_institution_courses', 'assigned_by'),
    ('student_progress', 'grantee_id'), ('student_progress', 'reviewed_by'),
    ('otp', 'user_id'), ('activity_log', 'user_id');
  INSERT INTO rahbar_v8_fk_cols
    SELECT table_name, column_name FROM information_schema.columns
     WHERE table_schema = DATABASE() AND column_name IN ('created_by', 'updated_by')
       AND table_name NOT LIKE 'orphans\_%';
  SET done = 0;
  OPEN fk_cols;
  fk_loop: LOOP
    FETCH fk_cols INTO t_name, c_name;
    IF done THEN LEAVE fk_loop; END IF;
    CALL rahbar_v8_fk(t_name, c_name);
  END LOOP;
  CLOSE fk_cols;
  DROP TEMPORARY TABLE IF EXISTS rahbar_v8_fk_cols;
END //
DELIMITER ;

CALL rahbar_v8();
DROP PROCEDURE IF EXISTS rahbar_v8;
DROP PROCEDURE IF EXISTS rahbar_v8_ref;
DROP PROCEDURE IF EXISTS rahbar_v8_fk;
