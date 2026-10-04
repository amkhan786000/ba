-- Adds the Office Coordinator role (role_id 8).
-- Access: empty dashboard, Payment Config, RCC Centers, Courses (+ institutions), Sponsors (map students only,
-- no contact info, no profile editing or bulk upload) and the Student Directory.
--
-- Run once (Flyway is not on the classpath, so this does not run automatically):
--   mysql -u <user> -p rahbar < V3__add_office_coordinator_role.sql
-- Then create users with this role from Admin > Manage Users.

INSERT INTO roles (role_id, role_name, description)
VALUES (8, 'Office Coordinator', 'Office operations: payment config, RCC centers, courses, sponsor-student mapping and student directory')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);
