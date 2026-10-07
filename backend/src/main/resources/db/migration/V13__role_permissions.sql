-- Role-based permissions for the admin screens, managed in Admin > Roles.
--
-- roles.permissions: comma-separated keys such as "USERS:VIEW,USERS:EDIT" (one VIEW / EDIT pair per screen).
-- roles.scope:       ALL, CHAPTER (only the user's own chapter) or RCC (only the user's own RCC center).
-- users.rcc_center_id: the RCC center an RCC coordinator looks after.
--
-- Starting permissions (only set where a role has none yet, so edits made in the UI are kept):
--   Super Admin, Application Administrator: everything (the Super Admin always has everything anyway)
--   Office Coordinator: users, payment config, payment dues, reports, RCC centers, courses, student directory
--   Chapter Lead (new, own chapter): chapters;  RCC Coordinator (new, own RCC center): RCC centers
--   Coordinator, Convenor, Sponsor, Student, Management keep using their own portals (no admin screens).

DROP PROCEDURE IF EXISTS rahbar_add_column;
DELIMITER //
CREATE PROCEDURE rahbar_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

CALL rahbar_add_column('roles', 'permissions', 'TEXT NULL');
CALL rahbar_add_column('roles', 'scope', "VARCHAR(20) NOT NULL DEFAULT 'ALL'");
CALL rahbar_add_column('users', 'rcc_center_id', 'BIGINT NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

SET @all_permissions = 'DASHBOARD:VIEW,DASHBOARD:EDIT,USERS:VIEW,USERS:EDIT,ROLES:VIEW,ROLES:EDIT,CHAPTERS:VIEW,CHAPTERS:EDIT,RCC_CENTERS:VIEW,RCC_CENTERS:EDIT,COURSES:VIEW,COURSES:EDIT,PAYMENT_CONFIG:VIEW,PAYMENT_CONFIG:EDIT,PAYMENT_DUES:VIEW,PAYMENT_DUES:EDIT,REPORTS:VIEW,REPORTS:EDIT,APPLICATION_PERIOD:VIEW,APPLICATION_PERIOD:EDIT,APPLICATIONS:VIEW,APPLICATIONS:EDIT,SPONSORSHIPS:VIEW,SPONSORSHIPS:EDIT,STUDENTS:VIEW,STUDENTS:EDIT,ACTIVITY:VIEW,ACTIVITY:EDIT';

UPDATE roles SET permissions = @all_permissions WHERE role_id IN (1, 2) AND permissions IS NULL;
UPDATE roles SET permissions = 'DASHBOARD:VIEW,USERS:VIEW,USERS:EDIT,PAYMENT_CONFIG:VIEW,PAYMENT_CONFIG:EDIT,PAYMENT_DUES:VIEW,PAYMENT_DUES:EDIT,REPORTS:VIEW,RCC_CENTERS:VIEW,RCC_CENTERS:EDIT,COURSES:VIEW,COURSES:EDIT,STUDENTS:VIEW,STUDENTS:EDIT'
 WHERE role_id = 8 AND permissions IS NULL;
UPDATE roles SET permissions = '' WHERE role_id IN (3, 4, 5, 6, 7) AND permissions IS NULL;

-- New roles (ids continue after the highest existing one; skipped when a role with that name exists).
INSERT INTO roles (role_id, role_name, description, permissions, scope)
SELECT COALESCE(MAX(role_id), 0) + 1, 'Chapter Lead', 'Looks after their own chapter', 'CHAPTERS:VIEW,CHAPTERS:EDIT', 'CHAPTER'
  FROM roles WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE LOWER(r.role_name) = 'chapter lead');
INSERT INTO roles (role_id, role_name, description, permissions, scope)
SELECT COALESCE(MAX(role_id), 0) + 1, 'RCC Coordinator', 'Looks after their own RCC center', 'RCC_CENTERS:VIEW,RCC_CENTERS:EDIT', 'RCC'
  FROM roles WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE LOWER(r.role_name) = 'rcc coordinator');

-- users.rcc_center_id -> rcc_centers
DROP PROCEDURE IF EXISTS rahbar_v13_fk;
DELIMITER //
CREATE PROCEDURE rahbar_v13_fk()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
                 WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'rcc_center_id'
                   AND referenced_table_name = 'rcc_centers') THEN
    ALTER TABLE users ADD CONSTRAINT fk_users_rcc_center_id FOREIGN KEY (rcc_center_id) REFERENCES rcc_centers(rcc_center_id);
  END IF;
END //
DELIMITER ;
CALL rahbar_v13_fk();
DROP PROCEDURE IF EXISTS rahbar_v13_fk;
