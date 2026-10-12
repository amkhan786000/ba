-- Students > Notes to sponsors: a short update or thank-you note a student writes to their sponsor. The office
-- approves it (or returns it with a reason) before the sponsor sees it.
-- kind: UPDATE or THANK_YOU. status: PENDING, APPROVED or RETURNED.
CREATE TABLE IF NOT EXISTS student_notes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_user_id BIGINT NOT NULL,
  sponsor_user_id BIGINT NOT NULL,
  kind VARCHAR(10) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME(6) NULL,
  review_note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_student_notes_student (student_user_id),
  KEY idx_student_notes_sponsor (sponsor_user_id, status),
  KEY idx_student_notes_status (status),
  CONSTRAINT fk_student_notes_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_student_notes_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Whoever edits students may approve their notes; whoever sees students may read them.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'STUDENT_NOTES:VIEW', 'STUDENT_NOTES:EDIT')
 WHERE FIND_IN_SET('STUDENTS:EDIT', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('STUDENT_NOTES:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'STUDENT_NOTES:VIEW')
 WHERE FIND_IN_SET('STUDENTS:VIEW', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('STUDENT_NOTES:VIEW', COALESCE(permissions, '')) = 0;
