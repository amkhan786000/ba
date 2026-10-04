-- Baseline schema for Rahbar, adapted from the existing MySQL dump (rahbar.sql).
-- NOTE: Two tables/columns are used throughout the original Flask code but were
-- NOT present in either SQL dump shipped with the project (likely added directly
-- on the production DB after the last dump was taken):
--   - table `sponsor_references` (reference_id, user_id, sponsor_year, chapter, ...)
--     (removed again in V2__remove_sponsor_references.sql)
--   - `payments.student_proof_url`, `payments.updated_by`
-- They are included below, inferred from how routes/admin.py, sponsor.py and
-- convenor.py query/insert them. Please diff this against the live production
-- schema before running in production, and adjust if actual columns differ.

CREATE TABLE application_period (
  id BIGINT NOT NULL AUTO_INCREMENT,
  start_date DATETIME NULL,
  end_date DATETIME NULL,
  is_active TINYINT(1) DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
  role_id INT NOT NULL AUTO_INCREMENT,
  role_name VARCHAR(50) NOT NULL,
  description TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
  user_id VARCHAR(50) NOT NULL,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  sex ENUM('M','F') NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(15) NOT NULL,
  role_id INT NOT NULL,
  status VARCHAR(20) DEFAULT 'Active',
  region VARCHAR(100) NOT NULL DEFAULT 'Jeddah',
  year INT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_users_email (email),
  UNIQUE KEY uq_users_phone (phone),
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE permissions (
  permission_id INT NOT NULL AUTO_INCREMENT,
  permission_name VARCHAR(100) NOT NULL,
  description TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permissions (
  role_permission_id INT NOT NULL AUTO_INCREMENT,
  role_id INT NOT NULL,
  permission_id INT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (role_permission_id),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(role_id),
  CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE institutions (
  institution_id VARCHAR(50) NOT NULL,
  institution_name VARCHAR(255) NOT NULL,
  address VARCHAR(255) NULL,
  contact_number VARCHAR(15) NULL,
  email VARCHAR(100) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (institution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE courses (
  course_id BIGINT NOT NULL AUTO_INCREMENT,
  institution_id VARCHAR(50) NOT NULL,
  course_name VARCHAR(255) NOT NULL,
  course_description TEXT NULL,
  fees_per_semester DECIMAL(10,2) NOT NULL DEFAULT 0,
  number_of_semesters INT NOT NULL DEFAULT 8,
  PRIMARY KEY (course_id),
  CONSTRAINT fk_courses_institution FOREIGN KEY (institution_id) REFERENCES institutions(institution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rcc_centers (
  rcc_center_id BIGINT NOT NULL AUTO_INCREMENT,
  center_name VARCHAR(255) NOT NULL,
  incharge_name VARCHAR(255) NULL,
  contact_number VARCHAR(15) NULL,
  location TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (rcc_center_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE grantee_details (
  grantee_detail_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(50) NULL,
  name VARCHAR(255) NOT NULL,
  father_name VARCHAR(255) NULL,
  mother_name VARCHAR(255) NULL,
  father_profession VARCHAR(255) NULL,
  mother_profession VARCHAR(255) NULL,
  address TEXT NULL,
  average_annual_salary DECIMAL(10,2) NULL,
  rahbar_alumnus VARCHAR(1) DEFAULT 'N',
  rcc_name VARCHAR(255) NULL,
  course_applied VARCHAR(255) NULL,
  father_mobile VARCHAR(15) NULL,
  mother_mobile VARCHAR(15) NULL,
  student_mobile VARCHAR(15) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (grantee_detail_id),
  KEY idx_gd_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_status (
  status_id BIGINT NOT NULL AUTO_INCREMENT,
  grantee_detail_id BIGINT NOT NULL,
  status VARCHAR(40) DEFAULT 'draft',
  comments TEXT NULL,
  updated_by VARCHAR(50) NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (status_id),
  KEY idx_as_grantee_detail (grantee_detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bank_details (
  bank_detail_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(50) NOT NULL,
  bank_name VARCHAR(255) NOT NULL,
  account_number VARCHAR(20) NOT NULL,
  account_name VARCHAR(255) NULL,
  ifsc_code VARCHAR(11) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (bank_detail_id),
  KEY idx_bd_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE grantor_grantees (
  grantor_grantee_id BIGINT NOT NULL AUTO_INCREMENT,
  grantor_id VARCHAR(50) NOT NULL,
  grantee_id VARCHAR(50) NOT NULL,
  status VARCHAR(20) DEFAULT 'Pending',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (grantor_grantee_id),
  UNIQUE KEY uq_gg_grantee (grantee_id),
  KEY idx_gg_grantor (grantor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Inferred table: not in the original SQL dumps but required by routes/admin.py,
-- routes/sponsor.py and routes/convenor.py. See note at top of file.
CREATE TABLE sponsor_references (
  reference_id VARCHAR(50) NOT NULL,
  user_id VARCHAR(50) NOT NULL,
  sponsor_year VARCHAR(20) NULL,
  chapter VARCHAR(100) NULL,
  referral VARCHAR(255) NULL,
  installment_date DATE NULL,
  payment_months INT DEFAULT 0,
  confirm_credit_date DATE NULL,
  special_demand TEXT NULL,
  remarks TEXT NULL,
  mobile_1 VARCHAR(20) NULL,
  mobile_2 VARCHAR(20) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (reference_id),
  KEY idx_sr_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_schedules (
  schedule_id BIGINT NOT NULL AUTO_INCREMENT,
  amount DECIMAL(10,2) NOT NULL,
  year INT NOT NULL,
  status INT NOT NULL DEFAULT 1,
  deadline_date DATE NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by VARCHAR(50) NULL,
  PRIMARY KEY (schedule_id),
  UNIQUE KEY uq_ps_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  payment_id BIGINT NOT NULL AUTO_INCREMENT,
  grantor_id VARCHAR(50) NOT NULL,
  grantee_id VARCHAR(50) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  payment_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  receipt_url VARCHAR(255) NULL,
  student_proof_url VARCHAR(255) NULL,
  status VARCHAR(20) DEFAULT 'Pending',
  due_date DATE NULL,
  updated_by VARCHAR(50) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (payment_id),
  KEY idx_pay_grantor (grantor_id),
  KEY idx_pay_grantee (grantee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approvals (
  approval_id BIGINT NOT NULL AUTO_INCREMENT,
  payment_id BIGINT NOT NULL,
  approver_id VARCHAR(50) NOT NULL,
  status VARCHAR(20) DEFAULT 'Pending',
  comments TEXT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (approval_id),
  KEY idx_app_payment (payment_id),
  KEY idx_app_approver (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
  notification_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(50) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(10) DEFAULT 'Unread',
  notification_date DATE NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id),
  KEY idx_notif_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chats (
  chat_id BIGINT NOT NULL AUTO_INCREMENT,
  sender_id VARCHAR(50) NOT NULL,
  receiver_id VARCHAR(50) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(10) DEFAULT 'Sent',
  sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_institution_courses (
  user_id VARCHAR(50) NOT NULL,
  institution_id VARCHAR(50) NOT NULL,
  course_id BIGINT NOT NULL,
  assigned_by VARCHAR(50) NULL,
  assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_progress (
  progress_id BIGINT NOT NULL AUTO_INCREMENT,
  grantee_id VARCHAR(50) NOT NULL,
  marks VARCHAR(4) NULL,
  file_path VARCHAR(255) NULL,
  session VARCHAR(20) NOT NULL DEFAULT '0',
  year INT NOT NULL,
  updated_by VARCHAR(100) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (progress_id),
  KEY idx_sp_grantee (grantee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE system_config (
  config_id INT NOT NULL DEFAULT 1,
  fee_schedule DECIMAL(10,2) NULL,
  deadline DATE NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE otp (
  user_id VARCHAR(50) NOT NULL,
  otp VARCHAR(6) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status TINYINT(1) DEFAULT 0 COMMENT '0=Unused, 1=Used, 2=Expired',
  PRIMARY KEY (user_id, otp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO roles (role_id, role_name, description) VALUES
 (1, 'Super Admin', 'Overall control of the system'),
 (2, 'Application Administrator', 'Day-to-day operations management'),
 (3, 'Application Coordinator', 'Handling operational workflow and verification'),
 (4, 'Convenor', 'Regional chapter administration'),
 (5, 'Sponsor', 'Financial support providers'),
 (6, 'beneficiary', 'Scholarship recipients'),
 (7, 'Management', 'Strategic oversight and reporting');
