-- Existing students' college type from their user ID (their reference number): PVT001-2026 -> Private,
-- M001-2026 -> Govt. Only IDs of that shape (letters followed by a digit), so e.g. "MD..." codes are left alone;
-- other students keep the college type they have. Installments follow at the next start (syncAll). Re-runnable.
UPDATE users u
   SET u.college_type = 'PRIVATE'
 WHERE UPPER(u.user_id) REGEXP '^PVT[0-9]'
   AND (u.role_id = 6 OR EXISTS (SELECT 1 FROM user_additional_roles x WHERE x.user_id = u.id AND x.role_id = 6))
   AND (u.college_type IS NULL OR u.college_type <> 'PRIVATE');

UPDATE users u
   SET u.college_type = 'GOVT'
 WHERE UPPER(u.user_id) REGEXP '^M[0-9]'
   AND (u.role_id = 6 OR EXISTS (SELECT 1 FROM user_additional_roles x WHERE x.user_id = u.id AND x.role_id = 6))
   AND (u.college_type IS NULL OR u.college_type <> 'GOVT');
