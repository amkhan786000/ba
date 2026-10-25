-- Admin > Documents: a document can have a PDF or Word (.doc / .docx) file attached, with or without text. Earlier
-- versions keep the file they had. attachment_path: the stored file; attachment_name: its name as uploaded.
-- Re-runnable.
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
CALL rahbar_add_column('documents', 'attachment_path', 'VARCHAR(255) NULL');
CALL rahbar_add_column('documents', 'attachment_name', 'VARCHAR(255) NULL');
CALL rahbar_add_column('document_revisions', 'attachment_path', 'VARCHAR(255) NULL');
CALL rahbar_add_column('document_revisions', 'attachment_name', 'VARCHAR(255) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
