-- Leads see their own area (view only), and sponsors keep seeing students they sponsored in the past.

-- ------------------------------------------------------------------ view-only access for the leads
-- Chapter Lead (own chapter) and RCC Coordinator / coaching center lead (own RCC center). The server filters every
-- screen to their students; these are VIEW permissions only. Existing grants stay; the Super Admin can change them
-- in Roles & Permissions.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'STUDENTS:VIEW', 'APPLICATIONS:VIEW', 'REPORTS:VIEW', 'PAYMENT_DUES:VIEW', 'PAYMENT_RECORDS:VIEW',
       'SPONSORSHIPS:VIEW', 'ALUMNI:VIEW', 'CHAPTER_DASHBOARD:VIEW')
 WHERE LOWER(role_name) = 'chapter lead' AND FIND_IN_SET('STUDENTS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'STUDENTS:VIEW', 'APPLICATIONS:VIEW', 'REPORTS:VIEW', 'PAYMENT_DUES:VIEW', 'PAYMENT_RECORDS:VIEW')
 WHERE LOWER(role_name) = 'rcc coordinator' AND FIND_IN_SET('STUDENTS:VIEW', COALESCE(permissions, '')) = 0;

-- ------------------------------------------------------------------ sponsor mapping history
-- Every period a student was mapped to a sponsor (ended_at NULL = still mapped). Lets a sponsor see their past
-- students and those students' progress up to the day they moved on.
CREATE TABLE IF NOT EXISTS sponsor_mapping_history (
  history_id BIGINT NOT NULL AUTO_INCREMENT,
  grantee_id BIGINT NOT NULL,
  grantor_id BIGINT NOT NULL,
  started_at DATETIME(6) NOT NULL,
  ended_at DATETIME(6) NULL,
  PRIMARY KEY (history_id),
  KEY idx_mapping_history_grantor (grantor_id),
  KEY idx_mapping_history_grantee (grantee_id),
  CONSTRAINT fk_mapping_history_grantee FOREIGN KEY (grantee_id) REFERENCES users(id),
  CONSTRAINT fk_mapping_history_grantor FOREIGN KEY (grantor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Current mappings start the history (skipping the "unassigned" grantor, user code 12).
INSERT INTO sponsor_mapping_history (grantee_id, grantor_id, started_at, ended_at)
SELECT gg.grantee_id, gg.grantor_id, COALESCE(gg.created_at, NOW(6)), NULL
  FROM grantor_grantees gg JOIN users s ON s.id = gg.grantor_id
 WHERE s.user_id <> '12'
   AND NOT EXISTS (SELECT 1 FROM sponsor_mapping_history h WHERE h.grantee_id = gg.grantee_id AND h.grantor_id = gg.grantor_id);
