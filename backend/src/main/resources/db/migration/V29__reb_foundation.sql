-- Rahbar-e-Banaat (REB), the residential girls' Madrasa-cum-School, phase 1: academic setup, staff, the girls,
-- their guardians, enrollment per session and documents. Managed in the admin menu group "Rahbar-e-Banaat".
-- The girls are not Rahbar user accounts (they don't log in); a girl who later joins Rahbar's scholarship is linked
-- through reb_students.rahbar_user_id.

CREATE TABLE IF NOT EXISTS reb_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(30) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  is_current BIT NOT NULL DEFAULT 0,
  status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_sessions_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Class levels, kept across sessions. track: MADRASA, SCHOOL or BOTH. level_order decides promotion (next class
-- = next_class_id, else the next active class by level_order). board_exam marks the Bihar Board exam class(es).
CREATE TABLE IF NOT EXISTS reb_classes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  track VARCHAR(10) NOT NULL DEFAULT 'BOTH',
  level_order INT NOT NULL DEFAULT 0,
  next_class_id BIGINT NULL,
  board_exam BIT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_classes_name (name),
  CONSTRAINT fk_reb_classes_next FOREIGN KEY (next_class_id) REFERENCES reb_classes(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reb_staff (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  name VARCHAR(120) NOT NULL,
  designation VARCHAR(20) NOT NULL DEFAULT 'TEACHER',
  phone VARCHAR(20) NULL,
  email VARCHAR(150) NULL,
  qualification VARCHAR(150) NULL,
  joined_on DATE NULL,
  left_on DATE NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  notes VARCHAR(1000) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_staff_user (user_id),
  CONSTRAINT fk_reb_staff_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A class in a session (e.g. Class 6 A, 2026).
CREATE TABLE IF NOT EXISTS reb_sections (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  name VARCHAR(20) NOT NULL DEFAULT 'A',
  class_teacher_staff_id BIGINT NULL,
  room VARCHAR(40) NULL,
  capacity INT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_sections (session_id, class_id, name),
  CONSTRAINT fk_reb_sections_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_sections_class FOREIGN KEY (class_id) REFERENCES reb_classes(id),
  CONSTRAINT fk_reb_sections_teacher FOREIGN KEY (class_teacher_staff_id) REFERENCES reb_staff(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- assessment: WRITTEN, ORAL or PRACTICAL.
CREATE TABLE IF NOT EXISTS reb_subjects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(20) NULL,
  track VARCHAR(10) NOT NULL DEFAULT 'SCHOOL',
  assessment VARCHAR(12) NOT NULL DEFAULT 'WRITTEN',
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_subjects_name (name),
  UNIQUE KEY uq_reb_subjects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The curriculum of a class in a session.
CREATE TABLE IF NOT EXISTS reb_class_subjects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  max_marks INT NOT NULL DEFAULT 100,
  pass_marks INT NOT NULL DEFAULT 33,
  display_order INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_class_subjects (session_id, class_id, subject_id),
  CONSTRAINT fk_reb_class_subjects_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_class_subjects_class FOREIGN KEY (class_id) REFERENCES reb_classes(id),
  CONSTRAINT fk_reb_class_subjects_subject FOREIGN KEY (subject_id) REFERENCES reb_subjects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- School name, number prefixes and similar (key -> value).
CREATE TABLE IF NOT EXISTS reb_settings (
  setting_key VARCHAR(60) NOT NULL,
  setting_value VARCHAR(1000) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reb_teacher_assignments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  staff_id BIGINT NOT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_teacher_assignments (section_id, subject_id),
  CONSTRAINT fk_reb_assign_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_assign_section FOREIGN KEY (section_id) REFERENCES reb_sections(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_assign_subject FOREIGN KEY (subject_id) REFERENCES reb_subjects(id),
  CONSTRAINT fk_reb_assign_staff FOREIGN KEY (staff_id) REFERENCES reb_staff(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per girl. aadhaar_last4: only the last 4 digits are kept (the full number only in an uploaded document).
-- fee_plan: SELF, SPONSORED or FREE. status: ACTIVE, LEFT or PASSED_OUT.
CREATE TABLE IF NOT EXISTS reb_students (
  id BIGINT NOT NULL AUTO_INCREMENT,
  admission_no VARCHAR(30) NOT NULL,
  name VARCHAR(120) NOT NULL,
  date_of_birth DATE NULL,
  aadhaar_last4 CHAR(4) NULL,
  photo_path VARCHAR(255) NULL,
  blood_group VARCHAR(5) NULL,
  address VARCHAR(500) NULL,
  district VARCHAR(80) NULL,
  state VARCHAR(40) NULL,
  social_category VARCHAR(10) NULL,
  board_registration_no VARCHAR(40) NULL,
  previous_school VARCHAR(150) NULL,
  residential BIT NOT NULL DEFAULT 1,
  fee_plan VARCHAR(10) NOT NULL DEFAULT 'SELF',
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
  admitted_on DATE NULL,
  left_on DATE NULL,
  leaving_reason VARCHAR(500) NULL,
  rahbar_user_id BIGINT NULL,
  notes VARCHAR(2000) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_students_admission_no (admission_no),
  KEY idx_reb_students_status (status),
  CONSTRAINT fk_reb_students_rahbar_user FOREIGN KEY (rahbar_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reb_guardians (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  relation VARCHAR(20) NOT NULL DEFAULT 'FATHER',
  name VARCHAR(120) NOT NULL,
  phone VARCHAR(20) NULL,
  whatsapp VARCHAR(20) NULL,
  email VARCHAR(150) NULL,
  occupation VARCHAR(120) NULL,
  monthly_income DECIMAL(10,2) NULL,
  is_primary BIT NOT NULL DEFAULT 0,
  may_pick_up BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_guardians_student (student_id),
  CONSTRAINT fk_reb_guardians_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A girl in a section for a session. status: ACTIVE, PROMOTED, DETAINED, LEFT or COMPLETED.
CREATE TABLE IF NOT EXISTS reb_enrollments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  roll_no VARCHAR(10) NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_enrollments (student_id, session_id),
  KEY idx_reb_enrollments_section (section_id),
  CONSTRAINT fk_reb_enrollments_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_enrollments_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id),
  CONSTRAINT fk_reb_enrollments_section FOREIGN KEY (section_id) REFERENCES reb_sections(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- kind: BIRTH_CERTIFICATE, AADHAAR, PHOTO, TC, MARKSHEET or OTHER. Files are named reb_<student>_..., and only
-- people with "REB: private details" may open them.
CREATE TABLE IF NOT EXISTS reb_student_documents (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  file_path VARCHAR(255) NOT NULL,
  file_name VARCHAR(200) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_documents_student (student_id),
  CONSTRAINT fk_reb_documents_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Starting settings (changed in REB Setup > Settings).
INSERT INTO reb_settings (setting_key, setting_value, created_at, updated_at)
SELECT * FROM (
  SELECT 'school_name' AS k, 'Rahbar-e-Banaat School-cum-Madarsa' AS v, NOW(6) AS c, NOW(6) AS u UNION ALL
  SELECT 'school_address', '', NOW(6), NOW(6) UNION ALL
  SELECT 'principal_name', '', NOW(6), NOW(6) UNION ALL
  SELECT 'admission_prefix', 'REB', NOW(6), NOW(6)
) d WHERE NOT EXISTS (SELECT 1 FROM reb_settings);

-- Who may use REB: its own sections on Roles & permissions. By default the Application Administrator (2) and the
-- Office Coordinator (8); the Super Admin always has every key.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'REB_DASHBOARD:VIEW', 'REB_DASHBOARD:EDIT', 'REB_SETUP:VIEW', 'REB_SETUP:EDIT', 'REB_STAFF:VIEW', 'REB_STAFF:EDIT',
       'REB_STUDENTS:VIEW', 'REB_STUDENTS:EDIT', 'REB_STUDENT_PRIVATE:VIEW', 'REB_STUDENT_PRIVATE:EDIT',
       'REB_REPORTS:VIEW', 'REB_REPORTS:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('REB_STUDENTS:VIEW', COALESCE(permissions, '')) = 0;
