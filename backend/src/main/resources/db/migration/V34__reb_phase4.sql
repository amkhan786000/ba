-- Rahbar-e-Banaat phase 4: admission enquiries (from the website's "Apply for admission" form, phone calls or
-- visits), the Quran / Hifz progress of each girl (sabaq, sabqi, manzil), and hostel leave with gate passes.

-- status: NEW, CONTACTED, TEST (entrance test / interview), ADMITTED or NOT_ADMITTED. source: WEBSITE, PHONE or
-- WALK_IN. student_id: the girl admitted from this enquiry.
CREATE TABLE IF NOT EXISTS reb_admission_enquiries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  girl_name VARCHAR(120) NOT NULL,
  date_of_birth DATE NULL,
  guardian_name VARCHAR(120) NOT NULL,
  relation VARCHAR(20) NULL,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(150) NULL,
  district VARCHAR(80) NULL,
  state VARCHAR(40) NULL,
  class_sought VARCHAR(80) NULL,
  previous_school VARCHAR(150) NULL,
  residential BIT NOT NULL DEFAULT 1,
  message VARCHAR(2000) NULL,
  source VARCHAR(10) NOT NULL DEFAULT 'WEBSITE',
  status VARCHAR(12) NOT NULL DEFAULT 'NEW',
  notes VARCHAR(2000) NULL,
  student_id BIGINT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_enquiries_status (status),
  CONSTRAINT fk_reb_enquiries_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A day's Quran lesson of a girl. kind: SABAQ (new lesson), SABQI (recent revision) or MANZIL (older revision).
-- para 1-30; quality: EXCELLENT, GOOD or NEEDS_REVISION.
CREATE TABLE IF NOT EXISTS reb_quran_progress (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  date DATE NOT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'SABAQ',
  para INT NOT NULL,
  surah VARCHAR(60) NULL,
  from_ayah INT NULL,
  to_ayah INT NULL,
  quality VARCHAR(15) NOT NULL DEFAULT 'GOOD',
  completed_para BIT NOT NULL DEFAULT 0,
  remark VARCHAR(300) NULL,
  teacher_staff_id BIGINT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reb_quran_student_date (student_id, date),
  CONSTRAINT fk_reb_quran_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_quran_teacher FOREIGN KEY (teacher_staff_id) REFERENCES reb_staff(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A residential girl going home: only with a guardian who may take her (reb_guardians.may_pick_up).
-- status: OUT or RETURNED (overdue = still out after leave_to).
CREATE TABLE IF NOT EXISTS reb_hostel_leaves (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pass_no VARCHAR(30) NOT NULL,
  student_id BIGINT NOT NULL,
  guardian_id BIGINT NULL,
  leave_from DATETIME(6) NOT NULL,
  leave_to DATE NOT NULL,
  reason VARCHAR(300) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'OUT',
  returned_at DATETIME(6) NULL,
  returned_with VARCHAR(120) NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reb_hostel_leaves_pass (pass_no),
  KEY idx_reb_hostel_leaves_status (status),
  CONSTRAINT fk_reb_leaves_student FOREIGN KEY (student_id) REFERENCES reb_students(id) ON DELETE CASCADE,
  CONSTRAINT fk_reb_leaves_guardian FOREIGN KEY (guardian_id) REFERENCES reb_guardians(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Office roles get all three; REB Teachers record Quran progress of their own classes.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
       'REB_ENQUIRIES:VIEW', 'REB_ENQUIRIES:EDIT', 'REB_QURAN:VIEW', 'REB_QURAN:EDIT', 'REB_HOSTEL:VIEW', 'REB_HOSTEL:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('REB_ENQUIRIES:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'REB_QURAN:VIEW', 'REB_QURAN:EDIT')
 WHERE LOWER(role_name) = 'reb teacher' AND FIND_IN_SET('REB_QURAN:VIEW', COALESCE(permissions, '')) = 0;
