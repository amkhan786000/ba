-- The scholarship application follows the 2026 paper form (Bihar Anjuman Scholarship 2026, Rahbar students only):
-- who recommended the student (RCC or BA chapter) and the incharge, a Govt-college seat, Aadhaar (last 4 digits only;
-- the self-attested copy is uploaded), date of birth, nationality, education (10th / 12th / ITI / Diploma: year and
-- %), guardian, bank details and when the declarations were accepted. All new columns are optional for old rows.
-- Re-runnable: each column is added only when missing.
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
CALL rahbar_add_column('scholarship_applications', 'recommended_by', 'VARCHAR(10) NULL');
CALL rahbar_add_column('scholarship_applications', 'recommended_chapter', 'VARCHAR(150) NULL');
CALL rahbar_add_column('scholarship_applications', 'incharge_name', 'VARCHAR(120) NULL');
CALL rahbar_add_column('scholarship_applications', 'incharge_mobile', 'VARCHAR(20) NULL');
CALL rahbar_add_column('scholarship_applications', 'govt_seat', 'CHAR(1) NULL');
CALL rahbar_add_column('scholarship_applications', 'govt_seat_details', 'VARCHAR(500) NULL');
CALL rahbar_add_column('scholarship_applications', 'aadhaar_last4', 'CHAR(4) NULL');
CALL rahbar_add_column('scholarship_applications', 'date_of_birth', 'DATE NULL');
CALL rahbar_add_column('scholarship_applications', 'nationality', 'VARCHAR(60) NULL');
CALL rahbar_add_column('scholarship_applications', 'edu_10_year', 'SMALLINT NULL');
CALL rahbar_add_column('scholarship_applications', 'edu_10_percent', 'DECIMAL(5,2) NULL');
CALL rahbar_add_column('scholarship_applications', 'edu_12_year', 'SMALLINT NULL');
CALL rahbar_add_column('scholarship_applications', 'edu_12_percent', 'DECIMAL(5,2) NULL');
CALL rahbar_add_column('scholarship_applications', 'iti_year', 'SMALLINT NULL');
CALL rahbar_add_column('scholarship_applications', 'iti_percent', 'DECIMAL(5,2) NULL');
CALL rahbar_add_column('scholarship_applications', 'diploma_year', 'SMALLINT NULL');
CALL rahbar_add_column('scholarship_applications', 'diploma_percent', 'DECIMAL(5,2) NULL');
CALL rahbar_add_column('scholarship_applications', 'guardian_name', 'VARCHAR(120) NULL');
CALL rahbar_add_column('scholarship_applications', 'guardian_mobile', 'VARCHAR(20) NULL');
CALL rahbar_add_column('scholarship_applications', 'bank_account_holder', 'VARCHAR(120) NULL');
CALL rahbar_add_column('scholarship_applications', 'bank_account_number', 'VARCHAR(30) NULL');
CALL rahbar_add_column('scholarship_applications', 'bank_ifsc', 'VARCHAR(11) NULL');
CALL rahbar_add_column('scholarship_applications', 'bank_name', 'VARCHAR(120) NULL');
CALL rahbar_add_column('scholarship_applications', 'bank_branch', 'VARCHAR(120) NULL');
CALL rahbar_add_column('scholarship_applications', 'declarations_accepted_at', 'DATETIME NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;
