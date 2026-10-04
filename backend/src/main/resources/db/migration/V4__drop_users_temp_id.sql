-- Drop users.temp_id.
--
-- The column exists only in the live database (it is not in the original SQL dump, and neither the
-- Flask app nor the Java/Angular code reads or writes it). It is NOT NULL without a default, so every
-- user insert from the Java app failed under MySQL strict mode with
-- "Field 'temp_id' doesn't have a default value" (Manage Users > Add, registration, bulk/manual student add).
-- Any index on the column is removed with it.
--
-- Run once (Flyway is not on the classpath, so this does not run automatically), after a backup:
--   mysqldump -u <user> -p rahbar users > users-before-v4.sql
--   mysql -u <user> -p rahbar < V4__drop_users_temp_id.sql

ALTER TABLE users DROP COLUMN temp_id;
