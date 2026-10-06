-- Chapters: the list behind the Chapter dropdown on users (separate from RCC centers).
-- A user's chapter is stored by name in users.region, and is optional (NULL = no chapter).
--
-- Idempotent and safe to re-run. Works on MySQL 5.7+/8 and MariaDB.
--
-- Run once (Flyway is not on the classpath, so this does not run automatically), after a backup:
--   mysqldump -u <user> -p rahbar > rahbar-before-v7.sql
--   mysql -u <user> -p rahbar < V7__chapters.sql

CREATE TABLE IF NOT EXISTS chapters (
  chapter_id BIGINT NOT NULL AUTO_INCREMENT,
  chapter_name VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  created_by VARCHAR(50) NULL,
  updated_by VARCHAR(50) NULL,
  created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (chapter_id),
  UNIQUE KEY uq_chapters_name (chapter_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A user does not have to belong to a chapter.
ALTER TABLE users MODIFY region VARCHAR(100) NULL DEFAULT NULL;

-- The chapters we have so far.
INSERT IGNORE INTO chapters (chapter_name) VALUES
  ('Jeddah Chapter'),
  ('Muscat'),
  ('USA'),
  ('Patna included'),
  ('Madinah'),
  ('Bangaluru'),
  ('Riyadh'),
  ('Jubail'),
  ('Qatar'),
  ('Dammam Al Khobar');

-- Seed one chapter per chapter name already used by users, so existing values appear in the dropdown.
INSERT IGNORE INTO chapters (chapter_name)
SELECT DISTINCT TRIM(region) FROM users WHERE region IS NOT NULL AND TRIM(region) <> '';
