-- Two separate books of income & expenses:
--   RAHBAR: Rahbar's (the NGO's) own money - funds for the office and the work, and what is spent.
--           Administration > Rahbar Income & Expenses, permission FINANCE.
--   REB:    Rahbar-e-Banaat's money. Rahbar-e-Banaat > REB Income & Expenses, permission REB_FINANCE.
-- Each has its own categories, entries and published years; neither screen, summary nor dashboard shows the other.
-- Everything recorded so far is Rahbar's.

ALTER TABLE fin_categories
  ADD COLUMN book VARCHAR(10) NOT NULL DEFAULT 'RAHBAR' AFTER id,
  DROP INDEX uq_fin_categories,
  ADD UNIQUE KEY uq_fin_categories_book (book, kind, name);

ALTER TABLE fin_entries
  ADD COLUMN book VARCHAR(10) NOT NULL DEFAULT 'RAHBAR' AFTER id,
  ADD KEY idx_fin_entries_book_date (book, entry_date);

ALTER TABLE fin_published_years
  DROP PRIMARY KEY,
  ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
  ADD COLUMN book VARCHAR(10) NOT NULL DEFAULT 'RAHBAR' AFTER id,
  ADD UNIQUE KEY uq_fin_published_years (book, year);

-- REB's own categories.
INSERT INTO fin_categories (book, name, kind, active, created_at, updated_at) VALUES
  ('REB', 'Donations', 'INCOME', 1, NOW(6), NOW(6)),
  ('REB', 'Zakat', 'INCOME', 1, NOW(6), NOW(6)),
  ('REB', 'Grants', 'INCOME', 1, NOW(6), NOW(6)),
  ('REB', 'Other income', 'INCOME', 1, NOW(6), NOW(6)),
  ('REB', 'Salaries', 'EXPENSE', 1, NOW(6), NOW(6)),
  ('REB', 'Food & hostel', 'EXPENSE', 1, NOW(6), NOW(6)),
  ('REB', 'Books & stationery', 'EXPENSE', 1, NOW(6), NOW(6)),
  ('REB', 'Rent & utilities', 'EXPENSE', 1, NOW(6), NOW(6)),
  ('REB', 'Maintenance', 'EXPENSE', 1, NOW(6), NOW(6)),
  ('REB', 'Events', 'EXPENSE', 1, NOW(6), NOW(6)),
  ('REB', 'Other expenses', 'EXPENSE', 1, NOW(6), NOW(6));

-- Rahbar's book: only the Super Admin records entries; the Application Administrator (2) and the Chapter Lead (9)
-- may look (read only).
UPDATE roles
   SET permissions = NULLIF(TRIM(BOTH ',' FROM REPLACE(CONCAT(',', COALESCE(permissions, ''), ','), ',FINANCE:EDIT,', ',')), '')
 WHERE role_id = 2;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'FINANCE:VIEW')
 WHERE role_id IN (2, 9) AND FIND_IN_SET('FINANCE:VIEW', COALESCE(permissions, '')) = 0;

-- REB's book: whoever may see REB fees may look (read only); the Super Admin records entries.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'REB_FINANCE:VIEW')
 WHERE FIND_IN_SET('REB_FEES:VIEW', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('REB_FINANCE:VIEW', COALESCE(permissions, '')) = 0;
