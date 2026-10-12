-- Online payments through Razorpay (Indian payments only: UPI, Indian cards and net banking; Bihar Anjuman has no
-- FCRA registration, so international payments stay switched off in Razorpay).
--   * Sponsors who pay through Rahbar (users.pays_through_rahbar) pay their students' installments online; the office
--     then passes the money on to each student (online_payment_items.passed_on_*).
--   * General donations from biharanjuman.com (purpose DONATION).
-- Every paid online payment gets an 80G receipt (receipt_no). The Razorpay keys are server settings, never stored here.

ALTER TABLE users
  ADD COLUMN pays_through_rahbar BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN pan VARCHAR(10) NULL;

ALTER TABLE payments ADD COLUMN method VARCHAR(10) NULL;

CREATE TABLE IF NOT EXISTS online_payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  purpose VARCHAR(10) NOT NULL,              -- SPONSOR or DONATION
  status VARCHAR(10) NOT NULL DEFAULT 'CREATED', -- CREATED, PAID or FAILED
  amount DECIMAL(12,2) NOT NULL,
  gateway VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
  gateway_order_id VARCHAR(60) NULL,
  gateway_payment_id VARCHAR(60) NULL,
  sponsor_user_id BIGINT NULL,
  donor_name VARCHAR(150) NOT NULL,
  donor_email VARCHAR(150) NULL,
  donor_phone VARCHAR(30) NULL,
  donor_pan VARCHAR(10) NULL,
  donor_address VARCHAR(500) NULL,
  note VARCHAR(300) NULL,                    -- donations: what it is for (e.g. a project)
  citizen_declared BOOLEAN NOT NULL DEFAULT FALSE,
  receipt_no VARCHAR(30) NULL,
  paid_at DATETIME(6) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_online_payments_order (gateway_order_id),
  UNIQUE KEY uq_online_payments_receipt (receipt_no),
  KEY idx_online_payments_sponsor (sponsor_user_id),
  KEY idx_online_payments_status (status, purpose),
  CONSTRAINT fk_online_payments_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS online_payment_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  online_payment_id BIGINT NOT NULL,
  installment_id BIGINT NOT NULL,
  student_user_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  payment_id BIGINT NULL,                    -- the payments row made when it was paid
  passed_on_at DATE NULL,                    -- when the office sent it to the student's bank account
  passed_on_ref VARCHAR(100) NULL,
  passed_on_by BIGINT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_online_payment_items_payment (online_payment_id),
  KEY idx_online_payment_items_student (student_user_id),
  CONSTRAINT fk_online_payment_items_payment FOREIGN KEY (online_payment_id) REFERENCES online_payments(id) ON DELETE CASCADE,
  CONSTRAINT fk_online_payment_items_student FOREIGN KEY (student_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Application Administrator (2) and Office Coordinator (8) see online payments, pass money on to students and keep
-- the receipt details; the Super Admin always may.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'ONLINE_PAYMENTS:VIEW', 'ONLINE_PAYMENTS:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('ONLINE_PAYMENTS:EDIT', COALESCE(permissions, '')) = 0;
