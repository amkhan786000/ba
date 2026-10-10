-- Coaching center students: the RCC Coordinator (coaching center lead) adds and edits the personal and family
-- details of students at their own center; the office (Application Administrator, Office Coordinator) can do it
-- for every center. They are ordinary student accounts tagged with the center (users.rcc_center_id).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'RCC_STUDENTS:VIEW', 'RCC_STUDENTS:EDIT')
 WHERE (LOWER(role_name) = 'rcc coordinator' OR role_id IN (2, 8))
   AND FIND_IN_SET('RCC_STUDENTS:VIEW', COALESCE(permissions, '')) = 0;

-- A student added by a coaching center lead has no reference ID (users.user_id) until the office assigns one after
-- their application. The unique key stays (MySQL allows many NULLs in a unique key).
ALTER TABLE users MODIFY user_id VARCHAR(50) NULL;
