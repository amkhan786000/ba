-- A student's reference number is their user ID (users.user_id), so V56's separate users.reference_no goes away.
-- Any reference already generated into it becomes the student's user ID (unless another account has that ID).
-- Re-runnable: does nothing once the column is gone.
DROP PROCEDURE IF EXISTS rahbar_drop_reference_no;
DELIMITER //
CREATE PROCEDURE rahbar_drop_reference_no()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'reference_no') THEN
    UPDATE users u
      LEFT JOIN users o ON BINARY o.user_id = BINARY u.reference_no AND o.id <> u.id -- binary: the columns may have different collations
       SET u.user_id = u.reference_no
     WHERE u.reference_no IS NOT NULL AND u.reference_no <> '' AND o.id IS NULL;
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_reference_no') THEN
      ALTER TABLE users DROP INDEX uk_users_reference_no;
    END IF;
    ALTER TABLE users DROP COLUMN reference_no;
  END IF;
END //
DELIMITER ;
CALL rahbar_drop_reference_no();
DROP PROCEDURE IF EXISTS rahbar_drop_reference_no;
