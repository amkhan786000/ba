-- Coaching Center > Students follows the paper admission form: session (e.g. 2025-28), father's and mother's
-- mobiles, the class and roll no. at school, the parent / guardian who signs, the declaration and a photo; and the
-- admission process: Applied -> entrance test (roll no., present / absent) -> Selected (studying) / Not selected.
-- Existing students are already admitted (status ACTIVE). Re-runnable.
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
CALL rahbar_add_column('coaching_students', 'session_from', 'INT NULL');
CALL rahbar_add_column('coaching_students', 'session_to', 'INT NULL');
CALL rahbar_add_column('coaching_students', 'father_mobile', 'VARCHAR(20) NULL');
CALL rahbar_add_column('coaching_students', 'mother_mobile', 'VARCHAR(20) NULL');
CALL rahbar_add_column('coaching_students', 'school_class', 'VARCHAR(20) NULL');
CALL rahbar_add_column('coaching_students', 'school_roll_no', 'VARCHAR(30) NULL');
CALL rahbar_add_column('coaching_students', 'guardian_name', 'VARCHAR(150) NULL');
CALL rahbar_add_column('coaching_students', 'declaration_accepted', 'BIT NOT NULL DEFAULT 0');
CALL rahbar_add_column('coaching_students', 'photo_path', 'VARCHAR(300) NULL');
CALL rahbar_add_column('coaching_students', 'test_roll_no', 'VARCHAR(30) NULL');
CALL rahbar_add_column('coaching_students', 'test_attendance', 'VARCHAR(10) NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
