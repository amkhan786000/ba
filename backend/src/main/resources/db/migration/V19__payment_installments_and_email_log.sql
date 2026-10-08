-- Sponsor-student payment installments, the payment start date and frequency they are built from,
-- a log of every email the application sends, and the permissions for the new screens.

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
-- Students: the date the first installment is due (Academic section).
CALL rahbar_add_column('users', 'payment_start_date', 'DATE NULL');
-- Payment Config: an installment is due every 3 or 4 months (existing years keep the old quarterly schedule).
CALL rahbar_add_column('payment_schedules', 'frequency_months', 'INT NOT NULL DEFAULT 3');
DROP PROCEDURE IF EXISTS rahbar_add_column;

-- Existing students start from the day their course was assigned (what the old computed schedule used).
UPDATE users u JOIN student_institution_courses sic ON sic.user_id = u.id
   SET u.payment_start_date = DATE(sic.assigned_at)
 WHERE u.role_id = 6 AND u.payment_start_date IS NULL AND sic.assigned_at IS NOT NULL;

-- One row per installment a sponsor owes a student. Paid once linked to a Paid payment; otherwise Due once
-- due_date has passed, else Not Due. Rows are (re)built by the application (PaymentInstallmentService).
CREATE TABLE IF NOT EXISTS payment_installments (
  installment_id BIGINT NOT NULL AUTO_INCREMENT,
  grantee_id BIGINT NOT NULL,
  grantor_id BIGINT NOT NULL,
  installment_no INT NOT NULL,
  due_date DATE NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  payment_id BIGINT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (installment_id),
  UNIQUE KEY uq_installment_student_no (grantee_id, installment_no),
  UNIQUE KEY uq_installment_payment (payment_id),
  KEY idx_installment_grantor (grantor_id),
  KEY idx_installment_due (due_date),
  CONSTRAINT fk_payment_installments_grantee FOREIGN KEY (grantee_id) REFERENCES users(id),
  CONSTRAINT fk_payment_installments_grantor FOREIGN KEY (grantor_id) REFERENCES users(id),
  CONSTRAINT fk_payment_installments_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
  CONSTRAINT fk_payment_installments_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_payment_installments_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Every email the application sends (or fails to send), kept as evidence. Codes and passwords are hidden.
CREATE TABLE IF NOT EXISTS email_log (
  email_id BIGINT NOT NULL AUTO_INCREMENT,
  to_address VARCHAR(255) NOT NULL,
  recipient_user_id BIGINT NULL,
  subject VARCHAR(500) NULL,
  body MEDIUMTEXT NULL,
  attachments VARCHAR(2000) NULL,
  status VARCHAR(10) NOT NULL,
  error VARCHAR(1000) NULL,
  sent_by BIGINT NULL,
  sent_at DATETIME(6) NOT NULL,
  PRIMARY KEY (email_id),
  KEY idx_email_log_sent_at (sent_at),
  KEY idx_email_log_to (to_address),
  KEY idx_email_log_recipient (recipient_user_id),
  CONSTRAINT fk_email_log_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id),
  CONSTRAINT fk_email_log_sent_by FOREIGN KEY (sent_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- New screens: Payment Records (installments between sponsors and students) and Email Log.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'PAYMENT_RECORDS:VIEW', 'PAYMENT_RECORDS:EDIT', 'EMAIL_LOG:VIEW', 'EMAIL_LOG:EDIT')
 WHERE role_id IN (1, 2) AND FIND_IN_SET('PAYMENT_RECORDS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'PAYMENT_RECORDS:VIEW', 'PAYMENT_RECORDS:EDIT')
 WHERE role_id = 8 AND FIND_IN_SET('PAYMENT_RECORDS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'PAYMENT_RECORDS:VIEW')
 WHERE LOWER(role_name) = 'chapter lead' AND FIND_IN_SET('PAYMENT_RECORDS:VIEW', COALESCE(permissions, '')) = 0;
