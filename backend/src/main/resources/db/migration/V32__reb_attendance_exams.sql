-- Rahbar-e-Banaat phase 3: holiday calendar, daily attendance, grading scale, exams with a date sheet, marks,
-- published results (kept as they were when published) and report cards. A new role, REB Teacher, sees and marks
-- only the classes she teaches (role scope REB_CLASSES, from REB > Teachers & Staff > who teaches what).

CREATE TABLE IF NOT EXISTS reb_calendar (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  date_from DATE NOT NULL,
  date_to DATE NOT NULL,
  title VARCHAR(150) NOT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'HOLIDAY',
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_calendar_session (session_id),
  CONSTRAINT fk_reb_calendar_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One mark per girl per day. status: PRESENT, ABSENT, LATE or LEAVE (leave is left out of the percentage).
CREATE TABLE IF NOT EXISTS reb_attendance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  date DATE NOT NULL,
  status VARCHAR(10) NOT NULL,
  remark VARCHAR(200) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_attendance (student_id, date),
  KEY idx_reb_attendance_section_date (section_id, date),
  CONSTRAINT fk_reb_attendance_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_attendance_section FOREIGN KEY (section_id) REFERENCES reb_sections(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Grading scale of a session: a percentage from min_percent gets this grade (highest band that fits).
CREATE TABLE IF NOT EXISTS reb_grade_bands (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  min_percent DECIMAL(5,2) NOT NULL,
  grade VARCHAR(5) NOT NULL,
  remark VARCHAR(60) NULL,
  is_pass BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_grade_bands_session (session_id),
  CONSTRAINT fk_reb_grade_bands_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- kind: UNIT_TEST, TERM, ANNUAL or ORAL. weight_percent: share in the annual result. status: DRAFT (set the date
-- sheet), MARKS_ENTRY (teachers enter marks) or PUBLISHED (results kept, marks locked).
CREATE TABLE IF NOT EXISTS reb_exams (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'TERM',
  weight_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
  start_date DATE NULL,
  end_date DATE NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
  published_at DATETIME(6) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_exams_session (session_id),
  CONSTRAINT fk_reb_exams_session FOREIGN KEY (session_id) REFERENCES reb_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The date sheet: one subject of one class in an exam.
CREATE TABLE IF NOT EXISTS reb_exam_papers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  subject_id BIGINT NOT NULL,
  exam_date DATE NULL,
  max_marks INT NOT NULL DEFAULT 100,
  pass_marks INT NOT NULL DEFAULT 33,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_exam_papers (exam_id, class_id, subject_id),
  CONSTRAINT fk_reb_exam_papers_exam FOREIGN KEY (exam_id) REFERENCES reb_exams(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_exam_papers_class FOREIGN KEY (class_id) REFERENCES reb_classes(id),
  CONSTRAINT fk_reb_exam_papers_subject FOREIGN KEY (subject_id) REFERENCES reb_subjects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reb_marks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  marks DECIMAL(6,2) NULL,
  absent BIT NOT NULL DEFAULT 0,
  remark VARCHAR(200) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_marks (paper_id, student_id),
  CONSTRAINT fk_reb_marks_paper FOREIGN KEY (paper_id) REFERENCES reb_exam_papers(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_marks_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A girl's result in an exam as it was when published (and the class teacher's remark, which can be written
-- before publishing). result: PASS, FAIL or ABSENT.
CREATE TABLE IF NOT EXISTS reb_exam_results (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  section_id BIGINT NULL,
  madrasa_total DECIMAL(8,2) NULL,
  madrasa_max DECIMAL(8,2) NULL,
  school_total DECIMAL(8,2) NULL,
  school_max DECIMAL(8,2) NULL,
  total DECIMAL(8,2) NULL,
  max_total DECIMAL(8,2) NULL,
  percent DECIMAL(5,2) NULL,
  grade VARCHAR(5) NULL,
  rank_in_section INT NULL,
  result VARCHAR(10) NULL,
  attendance_percent DECIMAL(5,2) NULL,
  teacher_remark VARCHAR(500) NULL,
  published BIT NOT NULL DEFAULT 0,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_exam_results (exam_id, student_id),
  CONSTRAINT fk_reb_exam_results_exam FOREIGN KEY (exam_id) REFERENCES reb_exams(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_exam_results_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Office roles: attendance and exams. The Super Admin always has every key.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'REB_ATTENDANCE:VIEW', 'REB_ATTENDANCE:EDIT', 'REB_EXAMS:VIEW', 'REB_EXAMS:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('REB_ATTENDANCE:VIEW', COALESCE(permissions, '')) = 0;

-- REB Teacher: marks attendance and enters marks for the classes and subjects she teaches (no fees, no private
-- details). Give a teacher a Rahbar login with this role and link it in REB > Teachers & Staff.
-- (id continues after the highest existing one, as in V13)
INSERT INTO roles (role_id, role_name, description, permissions, scope)
SELECT COALESCE(MAX(role_id), 0) + 1, 'REB Teacher', 'Rahbar-e-Banaat teacher: attendance and marks of her own classes',
       'REB_STUDENTS:VIEW,REB_ATTENDANCE:VIEW,REB_ATTENDANCE:EDIT,REB_EXAMS:VIEW,REB_EXAMS:EDIT', 'REB_CLASSES'
  FROM roles WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE LOWER(r.role_name) = 'reb teacher');
