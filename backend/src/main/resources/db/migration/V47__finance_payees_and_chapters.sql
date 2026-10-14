-- Income & expenses: who money comes from and goes to.
--   Income  -> "Received from" can be a chapter (chapters table).
--   Expense -> "Paid to" can be someone on the book's "Paid to" list (fin_payees), kept per book like categories
--              (Rahbar's and REB's lists are separate).
-- Either can still be typed by hand ("someone else"). party keeps the name shown for every entry.
--
-- Written to work on the live database, which was upgraded from the database before Rahbar used Flyway (older
-- tables such as chapters may have other column types and text collations than a new database), and to finish
-- the job if a first attempt stopped half way (MySQL can't undo DDL): every step checks first.
--   * chapter_id has no foreign key (the type of chapters.chapter_id differs between databases); Rahbar checks it.
--   * names are compared as utf8mb4 / utf8mb4_unicode_ci on both sides, whatever each table's collation is.

CREATE TABLE IF NOT EXISTS fin_payees (
  id BIGINT NOT NULL AUTO_INCREMENT,
  book VARCHAR(10) NOT NULL DEFAULT 'RAHBAR',
  name VARCHAR(150) NOT NULL,
  phone VARCHAR(30) NULL,
  note VARCHAR(300) NULL,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_fin_payees (book, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS rahbar_add_column;
DROP PROCEDURE IF EXISTS rahbar_add_index;
DROP PROCEDURE IF EXISTS rahbar_add_fk;
DELIMITER //
CREATE PROCEDURE rahbar_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
CREATE PROCEDURE rahbar_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(200))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = tbl AND index_name = idx) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD KEY `', idx, '` (', cols, ')');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
CREATE PROCEDURE rahbar_add_fk(IN tbl VARCHAR(64), IN fk VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                 WHERE table_schema = DATABASE() AND table_name = tbl AND constraint_name = fk) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD CONSTRAINT `', fk, '` ', definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

CALL rahbar_add_column('fin_entries', 'chapter_id', 'BIGINT NULL');
CALL rahbar_add_column('fin_entries', 'payee_id', 'BIGINT NULL');
CALL rahbar_add_index('fin_entries', 'idx_fin_entries_chapter', 'chapter_id');
CALL rahbar_add_fk('fin_entries', 'fk_fin_entries_payee', 'FOREIGN KEY (payee_id) REFERENCES fin_payees(id)');

DROP PROCEDURE IF EXISTS rahbar_add_column;
DROP PROCEDURE IF EXISTS rahbar_add_index;
DROP PROCEDURE IF EXISTS rahbar_add_fk;

-- Everyone already paid becomes the start of each book's "Paid to" list (IGNORE: names already there, or two
-- spellings the list counts as the same), and their entries point to them.
INSERT IGNORE INTO fin_payees (book, name, active, created_at, updated_at)
SELECT DISTINCT book, LEFT(TRIM(party), 150), 1, NOW(6), NOW(6) FROM fin_entries
 WHERE kind = 'EXPENSE' AND party IS NOT NULL AND TRIM(party) <> '';

UPDATE fin_entries e JOIN fin_payees p
    ON CONVERT(p.book USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(e.book USING utf8mb4) COLLATE utf8mb4_unicode_ci
   AND CONVERT(p.name USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(LEFT(TRIM(e.party), 150) USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET e.payee_id = p.id
 WHERE e.kind = 'EXPENSE' AND e.payee_id IS NULL;

-- Income that was typed as a chapter's name points to that chapter.
UPDATE fin_entries e JOIN chapters c
    ON CONVERT(TRIM(e.party) USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(TRIM(c.chapter_name) USING utf8mb4) COLLATE utf8mb4_unicode_ci
   SET e.chapter_id = c.chapter_id
 WHERE e.kind = 'INCOME' AND e.chapter_id IS NULL;
