-- Income & expenses: who money comes from and goes to.
--   Income  -> "Received from" can be a chapter (chapters table).
--   Expense -> "Paid to" can be someone on the book's "Paid to" list (fin_payees), kept per book like categories
--              (Rahbar's and REB's lists are separate).
-- Either can still be typed by hand ("someone else"). party keeps the name shown for every entry.
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

ALTER TABLE fin_entries
  ADD COLUMN chapter_id BIGINT NULL AFTER project_id,
  ADD COLUMN payee_id BIGINT NULL AFTER chapter_id,
  ADD CONSTRAINT fk_fin_entries_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id),
  ADD CONSTRAINT fk_fin_entries_payee FOREIGN KEY (payee_id) REFERENCES fin_payees(id);

-- Everyone already paid becomes the start of each book's "Paid to" list, and their entries point to them.
INSERT INTO fin_payees (book, name, active, created_at, updated_at)
SELECT DISTINCT book, TRIM(party), 1, NOW(6), NOW(6) FROM fin_entries
 WHERE kind = 'EXPENSE' AND party IS NOT NULL AND TRIM(party) <> '';
UPDATE fin_entries e JOIN fin_payees p ON p.book = e.book AND p.name = TRIM(e.party)
   SET e.payee_id = p.id
 WHERE e.kind = 'EXPENSE';

-- Income that was typed as a chapter's name points to that chapter.
UPDATE fin_entries e JOIN chapters c ON LOWER(TRIM(e.party)) = LOWER(TRIM(c.chapter_name))
   SET e.chapter_id = c.chapter_id
 WHERE e.kind = 'INCOME';
