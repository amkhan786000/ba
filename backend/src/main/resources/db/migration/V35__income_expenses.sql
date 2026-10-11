-- Administration > Income & Expenses: the organisation's money in and out besides the sponsor payments and REB fees
-- (which Rahbar already records and the summary adds in). Categories are managed by the admin; an entry can be
-- linked to a website project ("where the money went"). A year's summary can be shown on biharanjuman.com.

CREATE TABLE IF NOT EXISTS fin_categories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  kind VARCHAR(10) NOT NULL,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_fin_categories (kind, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- kind: INCOME or EXPENSE. method: CASH, UPI, BANK or CHEQUE. party: who paid / who was paid.
CREATE TABLE IF NOT EXISTS fin_entries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  entry_date DATE NOT NULL,
  kind VARCHAR(10) NOT NULL,
  category_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  amount DECIMAL(12,2) NOT NULL,
  party VARCHAR(150) NULL,
  description VARCHAR(500) NULL,
  method VARCHAR(10) NOT NULL DEFAULT 'BANK',
  reference VARCHAR(100) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_fin_entries_date (entry_date),
  CONSTRAINT fk_fin_entries_category FOREIGN KEY (category_id) REFERENCES fin_categories(id),
  CONSTRAINT fk_fin_entries_project FOREIGN KEY (project_id) REFERENCES web_projects(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Years whose summary (totals by category and project, no names) is shown on the website.
CREATE TABLE IF NOT EXISTS fin_published_years (
  year INT NOT NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fin_categories (name, kind, created_at, updated_at)
SELECT * FROM (
  SELECT 'Donations' AS n, 'INCOME' AS k, NOW(6) AS c, NOW(6) AS u UNION ALL
  SELECT 'Zakat', 'INCOME', NOW(6), NOW(6) UNION ALL
  SELECT 'Membership fees', 'INCOME', NOW(6), NOW(6) UNION ALL
  SELECT 'Other income', 'INCOME', NOW(6), NOW(6) UNION ALL
  SELECT 'Salaries', 'EXPENSE', NOW(6), NOW(6) UNION ALL
  SELECT 'Rent & utilities', 'EXPENSE', NOW(6), NOW(6) UNION ALL
  SELECT 'Food & hostel', 'EXPENSE', NOW(6), NOW(6) UNION ALL
  SELECT 'Books & stationery', 'EXPENSE', NOW(6), NOW(6) UNION ALL
  SELECT 'Events', 'EXPENSE', NOW(6), NOW(6) UNION ALL
  SELECT 'Other expenses', 'EXPENSE', NOW(6), NOW(6)
) d WHERE NOT EXISTS (SELECT 1 FROM fin_categories);

-- The Application Administrator keeps the books by default; the Super Admin always has every key.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'FINANCE:VIEW', 'FINANCE:EDIT')
 WHERE role_id = 2 AND FIND_IN_SET('FINANCE:VIEW', COALESCE(permissions, '')) = 0;
