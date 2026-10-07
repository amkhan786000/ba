-- A user's chapter becomes a reference: users.region (chapter name as text) -> users.chapter_id (FK to chapters).
--
-- - users.chapter_id is filled from users.region where the text matches a chapter name (case and surrounding
--   spaces ignored). Users whose text matches no chapter get no chapter (NULL).
-- - The old text column is kept for now, renamed to users.region_old (nothing reads it any more), so the
--   original values - including unmatched ones - can still be checked. It can be dropped in a later release.
--
-- Safe in either deploy order: the new backend only adds an empty chapter_id column if it starts first.
-- Re-running is a no-op once users.region has been renamed. Works on MySQL 5.7+/8 and MariaDB.
--
-- Run once, after a backup:
--   mysqldump -u <user> -p rahbar > rahbar-before-v9.sql
--   mysql -u <user> -p rahbar < V9__users_chapter_id.sql
-- (V7__chapters.sql must have been run first: it creates the chapters table.)

DROP PROCEDURE IF EXISTS rahbar_v9;
DELIMITER //
CREATE PROCEDURE rahbar_v9()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'region') THEN

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'chapter_id') THEN
      ALTER TABLE users ADD COLUMN chapter_id BIGINT NULL;
    END IF;

    UPDATE users u
      JOIN chapters c
        ON CONVERT(TRIM(c.chapter_name) USING utf8mb4) COLLATE utf8mb4_general_ci
         = CONVERT(TRIM(u.region) USING utf8mb4) COLLATE utf8mb4_general_ci
       SET u.chapter_id = c.chapter_id
     WHERE u.chapter_id IS NULL AND u.region IS NOT NULL AND TRIM(u.region) <> '';

    ALTER TABLE users CHANGE COLUMN region region_old VARCHAR(100) NULL DEFAULT NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
                 WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'chapter_id'
                   AND referenced_table_name = 'chapters') THEN
    ALTER TABLE users ADD CONSTRAINT fk_users_chapter_id FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id);
  END IF;
END //
DELIMITER ;

CALL rahbar_v9();
DROP PROCEDURE IF EXISTS rahbar_v9;
