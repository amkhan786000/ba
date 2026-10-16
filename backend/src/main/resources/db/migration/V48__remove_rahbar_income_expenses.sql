-- Rahbar's income & expenses (Administration > Rahbar Income & Expenses) is removed, with its data. REB's book
-- (Rahbar-e-Banaat > REB Income & Expenses) is untouched: every delete below is limited to book = 'RAHBAR'.
-- Entries first (they point to categories and to the "Paid to" list). Re-runnable: deleting nothing is fine.

DELETE FROM fin_entries WHERE book = 'RAHBAR';
DELETE FROM fin_payees WHERE book = 'RAHBAR';
DELETE FROM fin_categories WHERE book = 'RAHBAR';
DELETE FROM fin_published_years WHERE book = 'RAHBAR';

-- The "Rahbar income & expenses" permission (FINANCE:VIEW / FINANCE:EDIT) no longer exists; REB_FINANCE stays.
UPDATE roles
   SET permissions = NULLIF(TRIM(BOTH ',' FROM
         REPLACE(REPLACE(CONCAT(',', COALESCE(permissions, ''), ','), ',FINANCE:VIEW,', ','), ',FINANCE:EDIT,', ',')), '')
 WHERE FIND_IN_SET('FINANCE:VIEW', COALESCE(permissions, '')) > 0 OR FIND_IN_SET('FINANCE:EDIT', COALESCE(permissions, '')) > 0;
