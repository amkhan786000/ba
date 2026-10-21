-- On-behalf sponsorships: a sponsor can sponsor a student on behalf of an anonymous donor. The sponsor stays the
-- sponsor of record and the contact; the donor gets no account, only the chapter they belong to and a short label
-- (e.g. "Donor A", never a name). Kept on the current mapping and on the history. Re-runnable.
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
CALL rahbar_add_column('grantor_grantees', 'on_behalf', 'BIT NOT NULL DEFAULT 0');
CALL rahbar_add_column('grantor_grantees', 'donor_chapter_id', 'BIGINT NULL');
CALL rahbar_add_column('grantor_grantees', 'donor_label', 'VARCHAR(80) NULL');
CALL rahbar_add_column('sponsor_mapping_history', 'on_behalf', 'BIT NOT NULL DEFAULT 0');
CALL rahbar_add_column('sponsor_mapping_history', 'donor_chapter_id', 'BIGINT NULL');
CALL rahbar_add_column('sponsor_mapping_history', 'donor_label', 'VARCHAR(80) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
