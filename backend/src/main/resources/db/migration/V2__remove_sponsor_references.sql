-- Remove the "commitment reference" concept: students and payments now point straight at the sponsor.
--
-- Before: grantor_grantees.grantor_id and payments.grantor_id held either a sponsor's user_id
--         or a sponsor_references.reference_id (which in turn pointed to the sponsor's user_id).
-- After:  both columns always hold the sponsor's user_id, and sponsor_references is dropped.
--
-- Run this ONCE against the database, after backing it up:
--   mysqldump -u <user> -p rahbar > rahbar-before-v2.sql
--   mysql -u <user> -p rahbar < V2__remove_sponsor_references.sql
-- Note: the reference rows (sponsor year, referral, installment date, months, credit date,
-- special demand, remarks, mobile numbers) are deleted with the table. Keep the backup if you
-- may need that history.

START TRANSACTION;

-- 1. Students mapped to a reference -> mapped to that reference's sponsor.
UPDATE grantor_grantees gg
JOIN sponsor_references sr ON gg.grantor_id COLLATE utf8mb4_general_ci = sr.reference_id COLLATE utf8mb4_general_ci
SET gg.grantor_id = sr.user_id;

-- 2. Payments recorded against a reference -> recorded against that reference's sponsor.
UPDATE payments p
JOIN sponsor_references sr ON p.grantor_id COLLATE utf8mb4_general_ci = sr.reference_id COLLATE utf8mb4_general_ci
SET p.grantor_id = sr.user_id;

COMMIT;

-- 3. Drop the table (DDL commits on its own in MySQL, so it runs after the data is converted).
DROP TABLE IF EXISTS sponsor_references;
