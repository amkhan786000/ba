-- Scholarship applications get their own table. Until now "Apply for a scholarship" wrote into grantee_details, which
-- also holds every student's family details, so Admin > Applications listed all students as applications.
--
-- New flow: the public form saves here (status APPLIED) -> IN_PROCESS -> INTERVIEW -> ACCEPTED or REJECTED.
-- Accepting copies the applicant into users (role Student) and grantee_details (family details) and links the account
-- (student_user_id). The status history, documents and interview reviews of these applications point here through
-- the new application_id column; their old grantee_detail_id rows stay as they are (applications from before this
-- change are not moved: the new screens start fresh).
CREATE TABLE IF NOT EXISTS scholarship_applications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  sex VARCHAR(1) NULL,                        -- F / M
  student_mobile VARCHAR(20) NOT NULL,
  father_name VARCHAR(255) NULL,
  father_mobile VARCHAR(20) NULL,
  father_profession VARCHAR(255) NULL,
  mother_name VARCHAR(255) NULL,
  mother_mobile VARCHAR(20) NULL,
  mother_profession VARCHAR(255) NULL,
  address TEXT NULL,
  average_annual_salary DECIMAL(12,2) NULL,
  rahbar_alumnus VARCHAR(1) NOT NULL DEFAULT '0',
  rcc_name VARCHAR(255) NULL,
  course_applied VARCHAR(255) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'APPLIED', -- APPLIED, IN_PROCESS, INTERVIEW, ACCEPTED, REJECTED
  interview_at DATETIME(6) NULL,
  interview_venue VARCHAR(255) NULL,
  staff_note VARCHAR(2000) NULL,              -- written when accepting
  decided_at DATETIME(6) NULL,
  decided_by BIGINT NULL,
  student_user_id BIGINT NULL,                -- the account made (or linked) when accepted
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_scholarship_applications_status (status),
  KEY idx_scholarship_applications_created (created_at),
  KEY idx_scholarship_applications_email (email),
  CONSTRAINT fk_scholarship_applications_student FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_scholarship_applications_decided_by FOREIGN KEY (decided_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE application_status
  MODIFY grantee_detail_id BIGINT NULL,
  ADD COLUMN application_id BIGINT NULL AFTER grantee_detail_id,
  ADD KEY idx_application_status_application (application_id),
  ADD CONSTRAINT fk_application_status_application FOREIGN KEY (application_id) REFERENCES scholarship_applications(id);

ALTER TABLE application_documents
  MODIFY grantee_detail_id BIGINT NULL,
  ADD COLUMN application_id BIGINT NULL AFTER grantee_detail_id,
  ADD KEY idx_application_documents_application (application_id),
  ADD CONSTRAINT fk_application_documents_application FOREIGN KEY (application_id) REFERENCES scholarship_applications(id);

ALTER TABLE interview_reviews
  MODIFY grantee_detail_id BIGINT NULL,
  ADD COLUMN application_id BIGINT NULL AFTER grantee_detail_id,
  ADD UNIQUE KEY uq_interview_review_application (application_id, interviewer_id),
  ADD CONSTRAINT fk_interview_reviews_scholarship_application FOREIGN KEY (application_id) REFERENCES scholarship_applications(id);
