-- Students > Mentoring: a volunteer (mentor / tutor sign-up from the website) or a graduate (alumni who agreed to be
-- contacted) paired with a scholarship student. Exactly one of volunteer_id / alumni_user_id is set.
-- help: MENTOR or TUTOR. status: ACTIVE or ENDED.
CREATE TABLE IF NOT EXISTS mentorships (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_user_id BIGINT NOT NULL,
  volunteer_id BIGINT NULL,
  alumni_user_id BIGINT NULL,
  help VARCHAR(10) NOT NULL,
  focus VARCHAR(200) NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  started_on DATE NOT NULL,
  ended_on DATE NULL,
  notes VARCHAR(1000) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_mentorships_student (student_user_id),
  KEY idx_mentorships_volunteer (volunteer_id),
  KEY idx_mentorships_alumni (alumni_user_id),
  CONSTRAINT fk_mentorships_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_mentorships_volunteer FOREIGN KEY (volunteer_id) REFERENCES volunteer_applications(volunteer_id) ON DELETE SET NULL,
  CONSTRAINT fk_mentorships_alumni FOREIGN KEY (alumni_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Whoever follows up volunteers may pair them with students; whoever sees volunteers may see the pairs.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'MENTORING:VIEW', 'MENTORING:EDIT')
 WHERE FIND_IN_SET('VOLUNTEERS:EDIT', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('MENTORING:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'MENTORING:VIEW')
 WHERE FIND_IN_SET('VOLUNTEERS:VIEW', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('MENTORING:VIEW', COALESCE(permissions, '')) = 0;
