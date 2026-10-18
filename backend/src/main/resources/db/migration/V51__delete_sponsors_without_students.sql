-- One-time clean-up: delete sponsor accounts that have no student mapped to them, whether or not they have a
-- chapter. Runs once with the deploy (deploy.sh backs the database up first).
--
-- Kept (not deleted), so no history is lost and the migration can never fail on a foreign key:
--   * sponsors who sponsored before: sponsorship history, payments, installments, online / REB payments, notes;
--   * sponsors who also have another role (e.g. staff);
--   * the "unassigned" placeholder sponsor (user_id 12) and merged accounts;
--   * any sponsor still pointed to from anywhere else (every foreign key to users is checked).
-- Removed with each deleted sponsor: sign-in rows, notifications, extra roles and the office's follow-up notes.
-- The activity and email logs keep their rows (name / address as text); only the link is cleared.
-- Re-runnable: a second run finds nobody new to keep or delete beyond the same rule.

DROP TABLE IF EXISTS rahbar_del_sponsors;
CREATE TABLE rahbar_del_sponsors (id BIGINT NOT NULL PRIMARY KEY);

INSERT INTO rahbar_del_sponsors (id)
SELECT u.id
  FROM users u
 WHERE u.role_id = 5
   AND u.merged_into_id IS NULL
   AND (u.user_id IS NULL OR u.user_id <> '12')
   AND NOT EXISTS (SELECT 1 FROM grantor_grantees gg WHERE gg.grantor_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM sponsor_mapping_history h WHERE h.grantor_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.grantor_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM payment_installments i WHERE i.grantor_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM online_payments o WHERE o.sponsor_user_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM reb_sponsorships r WHERE r.sponsor_user_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM reb_payments rp WHERE rp.sponsor_user_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM student_notes n WHERE n.sponsor_user_id = u.id)
   AND NOT EXISTS (SELECT 1 FROM user_additional_roles ar WHERE ar.user_id = u.id AND ar.role_id <> 5);

-- Keep anyone still pointed to from a table not cleaned below (read from the database, so every foreign key counts).
DROP PROCEDURE IF EXISTS rahbar_keep_linked_sponsors;
DELIMITER //
CREATE PROCEDURE rahbar_keep_linked_sponsors()
BEGIN
  DECLARE tbl VARCHAR(64);
  DECLARE col VARCHAR(64);
  DECLARE done INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT DISTINCT k.table_name, k.column_name FROM information_schema.key_column_usage k
     WHERE k.table_schema = DATABASE() AND k.referenced_table_name = 'users' AND k.referenced_column_name = 'id'
       AND CONCAT(LOWER(k.table_name), '.', LOWER(k.column_name)) NOT IN (
           'activity_log.user_id', 'email_log.recipient_user_id', 'notifications.user_id', 'otp.user_id',
           'password_reset_codes.user_id', 'trusted_devices.user_id', 'push_subscriptions.user_id',
           'user_additional_roles.user_id', 'sponsor_followups.sponsor_user_id');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tbl, col;
    IF done = 1 THEN LEAVE read_loop; END IF;
    SET @ddl = CONCAT('DELETE d FROM rahbar_del_sponsors d WHERE EXISTS (SELECT 1 FROM `', tbl, '` t WHERE t.`', col,
                      '` = d.id', IF(LOWER(tbl) = 'users', ' AND t.id <> d.id', ''), ')');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END //
DELIMITER ;
CALL rahbar_keep_linked_sponsors();
DROP PROCEDURE IF EXISTS rahbar_keep_linked_sponsors;

-- History keeps its rows; only the link to the deleted account goes.
UPDATE activity_log SET user_id = NULL WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
UPDATE email_log SET recipient_user_id = NULL WHERE recipient_user_id IN (SELECT id FROM rahbar_del_sponsors);
-- Rows that only belong to these accounts.
DELETE FROM sponsor_followups     WHERE sponsor_user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM notifications         WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM otp                   WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM password_reset_codes  WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM trusted_devices       WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM push_subscriptions    WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM user_additional_roles WHERE user_id IN (SELECT id FROM rahbar_del_sponsors);
DELETE FROM users                 WHERE id IN (SELECT id FROM rahbar_del_sponsors);

DROP TABLE IF EXISTS rahbar_del_sponsors;
