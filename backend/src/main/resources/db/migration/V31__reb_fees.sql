-- Rahbar-e-Banaat phase 2: fees and sponsorships. Fee heads with an amount per class per session, concessions,
-- one bill (invoice) per girl per month with a sponsor share and a family share, payments with printed receipts
-- (a payment can pay several bills), and sponsorships by Rahbar sponsors (e.g. "Educate a girl: Rs. 500 per month").

-- Kinds of fee. frequency: MONTHLY (every bill), ANNUAL (once a session, in bill_month of the structure) or
-- ONE_TIME (only in the girl's first bill). residential_only: only for girls who live in the hostel.
CREATE TABLE IF NOT EXISTS reb_fee_heads (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  frequency VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',
  residential_only BIT NOT NULL DEFAULT 0,
  display_order INT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_fee_heads_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The amount of a head for a class in a session. bill_month (1-12): the month an ANNUAL head is billed.
CREATE TABLE IF NOT EXISTS reb_fee_structures (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  fee_head_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  bill_month INT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_fee_structures (session_id, class_id, fee_head_id),
  CONSTRAINT fk_reb_fee_structures_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_fee_structures_class FOREIGN KEY (class_id) REFERENCES reb_classes(id),
  CONSTRAINT fk_reb_fee_structures_head FOREIGN KEY (fee_head_id) REFERENCES reb_fee_heads(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A reduction for a girl in a session: kind PERCENT or AMOUNT (per bill), on one head or (empty) on all heads,
-- for months from_period..to_period (YYYY-MM; empty = the whole session).
CREATE TABLE IF NOT EXISTS reb_concessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  fee_head_id BIGINT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
  value DECIMAL(10,2) NOT NULL,
  reason VARCHAR(20) NOT NULL DEFAULT 'NEED',
  note VARCHAR(500) NULL,
  from_period CHAR(7) NULL,
  to_period CHAR(7) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_concessions_student (student_id),
  CONSTRAINT fk_reb_concessions_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_concessions_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_concessions_head FOREIGN KEY (fee_head_id) REFERENCES reb_fee_heads(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A Rahbar sponsor (users.id) supporting a girl with monthly_amount, from start_period to end_period (YYYY-MM;
-- empty end = ongoing). status: ACTIVE or ENDED.
CREATE TABLE IF NOT EXISTS reb_sponsorships (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  sponsor_user_id BIGINT NOT NULL,
  monthly_amount DECIMAL(10,2) NOT NULL,
  start_period CHAR(7) NOT NULL,
  end_period CHAR(7) NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_sponsorships_student (student_id),
  KEY idx_reb_sponsorships_sponsor (sponsor_user_id),
  CONSTRAINT fk_reb_sponsorships_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_sponsorships_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One bill per girl per month (period YYYY-MM; checked by the app, not counting cancelled bills, so a bill made by
-- mistake can be cancelled and made again). net = gross - concession; net = sponsor_share + family_share.
-- paid: allocated so far. status: DUE, PARTIAL, PAID, WAIVED or CANCELLED (overdue = due_date passed, not paid).
CREATE TABLE IF NOT EXISTS reb_invoices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_no VARCHAR(40) NOT NULL,
  student_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  period CHAR(7) NOT NULL,
  due_date DATE NOT NULL,
  gross DECIMAL(10,2) NOT NULL DEFAULT 0,
  concession DECIMAL(10,2) NOT NULL DEFAULT 0,
  net DECIMAL(10,2) NOT NULL DEFAULT 0,
  sponsor_share DECIMAL(10,2) NOT NULL DEFAULT 0,
  family_share DECIMAL(10,2) NOT NULL DEFAULT 0,
  sponsor_paid DECIMAL(10,2) NOT NULL DEFAULT 0,
  family_paid DECIMAL(10,2) NOT NULL DEFAULT 0,
  sponsorship_id BIGINT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'DUE',
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_invoices_no (invoice_no),
  KEY idx_reb_invoices_student_period (student_id, period),
  KEY idx_reb_invoices_period (period),
  CONSTRAINT fk_reb_invoices_student FOREIGN KEY (student_id) REFERENCES reb_students(id),
  CONSTRAINT fk_reb_invoices_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_invoices_sponsorship FOREIGN KEY (sponsorship_id) REFERENCES reb_sponsorships(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reb_invoice_lines (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_id BIGINT NOT NULL,
  fee_head_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  concession DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_invoice_lines_invoice (invoice_id),
  CONSTRAINT fk_reb_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES reb_invoices(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_invoice_lines_head FOREIGN KEY (fee_head_id) REFERENCES reb_fee_heads(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Money received. payer: FAMILY or SPONSOR (sponsor_user_id). method: CASH, UPI, BANK or CHEQUE.
-- Never deleted: status CANCELLED with a reason (its receipt number stays used).
CREATE TABLE IF NOT EXISTS reb_payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  receipt_no VARCHAR(40) NOT NULL,
  student_id BIGINT NOT NULL,
  payer VARCHAR(10) NOT NULL DEFAULT 'FAMILY',
  sponsor_user_id BIGINT NULL,
  amount DECIMAL(10,2) NOT NULL,
  paid_on DATE NOT NULL,
  method VARCHAR(10) NOT NULL DEFAULT 'CASH',
  reference VARCHAR(100) NULL,
  note VARCHAR(500) NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'VALID',
  cancel_reason VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_payments_receipt (receipt_no),
  KEY idx_reb_payments_student (student_id),
  KEY idx_reb_payments_paid_on (paid_on),
  CONSTRAINT fk_reb_payments_student FOREIGN KEY (student_id) REFERENCES reb_students(id),
  CONSTRAINT fk_reb_payments_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Which bills a payment paid, and how much of each (share: SPONSOR or FAMILY). A payment is always fully spread
-- over bills (no advances: to take money ahead, generate the coming months' bills first).
CREATE TABLE IF NOT EXISTS reb_payment_allocations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  payment_id BIGINT NOT NULL,
  invoice_id BIGINT NOT NULL,
  share VARCHAR(10) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_allocations_payment (payment_id),
  KEY idx_reb_allocations_invoice (invoice_id),
  CONSTRAINT fk_reb_allocations_payment FOREIGN KEY (payment_id) REFERENCES reb_payments(id),
  CONSTRAINT fk_reb_allocations_invoice FOREIGN KEY (invoice_id) REFERENCES reb_invoices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- New settings (REB Setup > Settings).
INSERT INTO reb_settings (setting_key, setting_value, created_at, updated_at)
SELECT * FROM (
  SELECT 'receipt_prefix' AS k, 'REB-R' AS v, NOW(6) AS c, NOW(6) AS u UNION ALL
  SELECT 'invoice_prefix', 'REB-B', NOW(6), NOW(6) UNION ALL
  SELECT 'due_day', '10', NOW(6), NOW(6) UNION ALL
  SELECT 'sponsorship_amount', '500', NOW(6), NOW(6)
) d WHERE NOT EXISTS (SELECT 1 FROM reb_settings WHERE setting_key = 'receipt_prefix');

-- Who may use fees and sponsorships: Application Administrator (2) and Office Coordinator (8); the Super Admin
-- always has every key.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'REB_FEES:VIEW', 'REB_FEES:EDIT', 'REB_FEE_APPROVALS:VIEW', 'REB_FEE_APPROVALS:EDIT',
       'REB_SPONSORSHIPS:VIEW', 'REB_SPONSORSHIPS:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('REB_FEES:VIEW', COALESCE(permissions, '')) = 0;
