-- Coaching center (RCC) records of their own, for classes 7 to 10: students (from the admission form), teachers,
-- subjects, and monthly exams with marks per subject and the 1st / 2nd / 3rd prizes. Starts empty: the student
-- accounts tagged with an RCC center stay where they are. Re-runnable.
-- The coaching center coordinator (the "RCC Coordinator" role, own center) adds and edits them; a coaching teacher
-- (new "Coaching Teacher" role, own center) may sign in and see the students, subjects, exams and marks.

CREATE TABLE IF NOT EXISTS coaching_students (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rcc_center_id BIGINT NOT NULL,
  admission_no VARCHAR(30) NULL,
  admission_date DATE NULL,
  class_level INT NOT NULL,
  name VARCHAR(150) NOT NULL,
  gender VARCHAR(1) NULL,
  date_of_birth DATE NULL,
  school_name VARCHAR(200) NULL,
  previous_class_percent DECIMAL(5,2) NULL,
  father_name VARCHAR(150) NULL,
  father_occupation VARCHAR(100) NULL,
  mother_name VARCHAR(150) NULL,
  guardian_mobile VARCHAR(20) NULL,
  student_mobile VARCHAR(20) NULL,
  family_income DECIMAL(12,2) NULL,
  address VARCHAR(500) NULL,
  aadhaar_last4 VARCHAR(4) NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  remarks VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_coaching_students_center_class (rcc_center_id, class_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coaching_teachers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rcc_center_id BIGINT NOT NULL,
  name VARCHAR(150) NOT NULL,
  gender VARCHAR(1) NULL,
  phone VARCHAR(20) NULL,
  email VARCHAR(150) NULL,
  qualification VARCHAR(150) NULL,
  subjects VARCHAR(300) NULL,
  classes VARCHAR(20) NULL,
  joining_date DATE NULL,
  address VARCHAR(500) NULL,
  active BIT NOT NULL DEFAULT 1,
  remarks VARCHAR(500) NULL,
  user_id BIGINT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_coaching_teachers_center (rcc_center_id),
  KEY idx_coaching_teachers_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coaching_subjects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coaching_subject_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO coaching_subjects (name, sort_order) VALUES
  ('Mathematics', 1), ('Science', 2), ('English', 3), ('Hindi', 4), ('Social Science', 5), ('Urdu', 6);

CREATE TABLE IF NOT EXISTS coaching_exams (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rcc_center_id BIGINT NOT NULL,
  class_level INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  exam_date DATE NOT NULL,
  prizes_generated_at DATETIME(6) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_coaching_exams_center (rcc_center_id, exam_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The subjects of an exam and their maximum marks.
CREATE TABLE IF NOT EXISTS coaching_exam_subjects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  max_marks DECIMAL(6,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coaching_exam_subject (exam_id, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A student's marks in one subject of an exam (no row: not entered / absent).
CREATE TABLE IF NOT EXISTS coaching_exam_marks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  marks DECIMAL(6,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coaching_exam_mark (exam_id, student_id, subject_id),
  KEY idx_coaching_exam_marks_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The 1st / 2nd / 3rd prizes of an exam (equal totals share a position).
CREATE TABLE IF NOT EXISTS coaching_exam_prizes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  position INT NOT NULL,
  total DECIMAL(8,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coaching_exam_prize (exam_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Teachers and exams: whoever may see / edit coaching center students may see / edit these too (changeable in Roles).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'COACHING_TEACHERS:VIEW', 'COACHING_EXAMS:VIEW')
 WHERE (FIND_IN_SET('RCC_STUDENTS:VIEW', COALESCE(permissions, '')) > 0 OR FIND_IN_SET('RCC_STUDENTS:EDIT', COALESCE(permissions, '')) > 0)
   AND FIND_IN_SET('COACHING_EXAMS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'COACHING_TEACHERS:EDIT', 'COACHING_EXAMS:EDIT')
 WHERE FIND_IN_SET('RCC_STUDENTS:EDIT', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('COACHING_EXAMS:EDIT', COALESCE(permissions, '')) = 0;

-- Coaching Teacher: sees their own center's students, teachers, subjects, exams and marks; changes nothing (the
-- Super Admin can let them enter marks in Roles: Coaching exams, manage). Give a teacher a login with this role and
-- their RCC center in Manage Users, then link it on the teacher in Coaching Center > Teachers.
-- (id continues after the highest existing one, as in V13)
INSERT INTO roles (role_id, role_name, description, permissions, scope)
SELECT COALESCE(MAX(role_id), 0) + 1, 'Coaching Teacher', 'Coaching center teacher: sees their center''s students, exams and marks',
       'RCC_STUDENTS:VIEW,COACHING_TEACHERS:VIEW,COACHING_EXAMS:VIEW', 'RCC'
  FROM roles WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE LOWER(r.role_name) = 'coaching teacher');
