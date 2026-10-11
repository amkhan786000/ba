-- Volunteer sign-ups from the public website (biharanjuman.com "Volunteer with us"): people offering to mentor,
-- tutor, sponsor students or join a district chapter. Staff follow them up in Admin > Volunteers.
CREATE TABLE IF NOT EXISTS volunteer_applications (
  volunteer_id BIGINT NOT NULL AUTO_INCREMENT,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150) NULL,
  phone VARCHAR(30) NULL,
  city VARCHAR(100) NULL,
  country VARCHAR(100) NULL,
  home_district VARCHAR(100) NULL,
  profession VARCHAR(150) NULL,
  wants_mentor BIT NOT NULL DEFAULT 0,
  wants_tutor BIT NOT NULL DEFAULT 0,
  wants_sponsor BIT NOT NULL DEFAULT 0,
  wants_chapter BIT NOT NULL DEFAULT 0,
  -- mentor: fields of expertise, comma-separated (e.g. "Career guidance,Engineering & IT")
  mentor_fields VARCHAR(500) NULL,
  -- tutor: subjects and grades, comma-separated; ONLINE, IN_PERSON or EITHER; optional RCC center
  tutor_subjects VARCHAR(500) NULL,
  tutor_grades VARCHAR(200) NULL,
  tutor_mode VARCHAR(20) NULL,
  rcc_center_id BIGINT NULL,
  hours_per_month INT NULL,
  -- sponsor: how many students they would like to sponsor
  sponsor_students INT NULL,
  -- chapter: the chapter to join, or none ("help start one in my district")
  chapter_id BIGINT NULL,
  message VARCHAR(2000) NULL,
  source VARCHAR(50) NULL,
  -- NEW, CONTACTED, ONBOARDED or DECLINED; staff notes are internal
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  notes VARCHAR(2000) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (volunteer_id),
  KEY idx_volunteer_applications_status (status),
  CONSTRAINT fk_volunteer_applications_center FOREIGN KEY (rcc_center_id) REFERENCES rcc_centers(rcc_center_id) ON DELETE SET NULL,
  CONSTRAINT fk_volunteer_applications_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE SET NULL,
  CONSTRAINT fk_volunteer_applications_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_volunteer_applications_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Application Administrator (2) and Office Coordinator (8) follow up volunteers (the Super Admin has every key).
-- Change it on the Roles & permissions page.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'VOLUNTEERS:VIEW', 'VOLUNTEERS:EDIT')
 WHERE role_id IN (2, 8)
   AND FIND_IN_SET('VOLUNTEERS:VIEW', COALESCE(permissions, '')) = 0;
