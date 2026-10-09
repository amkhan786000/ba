-- Interview scoring, alumni profiles, browser push notifications and merging duplicate students.

-- ------------------------------------------------------------------ interview scoring
-- Criteria interviewers score (1-10), managed by admins; inactive ones are kept for old scores.
CREATE TABLE IF NOT EXISTS interview_criteria (
  criterion_id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (criterion_id),
  UNIQUE KEY uq_interview_criteria_name (name),
  CONSTRAINT fk_interview_criteria_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_interview_criteria_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO interview_criteria (name, description, sort_order, active)
SELECT * FROM (
  SELECT 'Academics' AS name, 'Marks, subject knowledge and study habits' AS description, 1 AS sort_order, 1 AS active UNION ALL
  SELECT 'Financial need', 'How much the family needs support', 2, 1 UNION ALL
  SELECT 'Communication', 'Clarity and confidence in the interview', 3, 1 UNION ALL
  SELECT 'Motivation', 'Goals and commitment to complete the course', 4, 1
) d WHERE NOT EXISTS (SELECT 1 FROM interview_criteria);

-- One review per interviewer per application: a comment and a recommendation.
CREATE TABLE IF NOT EXISTS interview_reviews (
  review_id BIGINT NOT NULL AUTO_INCREMENT,
  grantee_detail_id BIGINT NOT NULL,
  interviewer_id BIGINT NOT NULL,
  recommendation VARCHAR(20) NULL,
  comment VARCHAR(2000) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (review_id),
  UNIQUE KEY uq_interview_review (grantee_detail_id, interviewer_id),
  CONSTRAINT fk_interview_reviews_application FOREIGN KEY (grantee_detail_id) REFERENCES grantee_details(grantee_detail_id),
  CONSTRAINT fk_interview_reviews_interviewer FOREIGN KEY (interviewer_id) REFERENCES users(id),
  CONSTRAINT fk_interview_reviews_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_interview_reviews_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The score (1-10) a review gives each criterion.
CREATE TABLE IF NOT EXISTS interview_scores (
  score_id BIGINT NOT NULL AUTO_INCREMENT,
  review_id BIGINT NOT NULL,
  criterion_id BIGINT NOT NULL,
  score INT NOT NULL,
  PRIMARY KEY (score_id),
  UNIQUE KEY uq_interview_score (review_id, criterion_id),
  CONSTRAINT fk_interview_scores_review FOREIGN KEY (review_id) REFERENCES interview_reviews(review_id),
  CONSTRAINT fk_interview_scores_criterion FOREIGN KEY (criterion_id) REFERENCES interview_criteria(criterion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------ alumni
-- What a graduated student does now; kept up to date by the office and by the graduate.
CREATE TABLE IF NOT EXISTS alumni_profiles (
  user_id BIGINT NOT NULL,
  current_status VARCHAR(30) NULL,
  organisation VARCHAR(200) NULL,
  role_title VARCHAR(200) NULL,
  city VARCHAR(100) NULL,
  country VARCHAR(100) NULL,
  linkedin_url VARCHAR(300) NULL,
  graduation_year INT NULL,
  consent_to_contact BIT NOT NULL DEFAULT 0,
  notes VARCHAR(2000) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_alumni_profiles_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_alumni_profiles_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_alumni_profiles_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------ browser push notifications
-- One row per browser / device a user allowed notifications on.
CREATE TABLE IF NOT EXISTS push_subscriptions (
  subscription_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  endpoint VARCHAR(1000) NOT NULL,
  endpoint_hash CHAR(64) NOT NULL,
  p256dh VARCHAR(200) NOT NULL,
  auth VARCHAR(100) NOT NULL,
  user_agent VARCHAR(300) NULL,
  created_at DATETIME(6) NOT NULL,
  last_success_at DATETIME(6) NULL,
  failures INT NOT NULL DEFAULT 0,
  PRIMARY KEY (subscription_id),
  UNIQUE KEY uq_push_endpoint (endpoint_hash),
  KEY idx_push_user (user_id),
  CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Small application settings (e.g. the generated VAPID key pair for push notifications).
CREATE TABLE IF NOT EXISTS app_settings (
  setting_key VARCHAR(100) NOT NULL,
  setting_value TEXT NOT NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------ merging duplicate students
DROP PROCEDURE IF EXISTS rahbar_add_column;
DELIMITER //
CREATE PROCEDURE rahbar_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;
-- A student account merged into another one (it is deactivated, never deleted).
CALL rahbar_add_column('users', 'merged_into_id', 'BIGINT NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

DROP PROCEDURE IF EXISTS rahbar_v21_fk;
DELIMITER //
CREATE PROCEDURE rahbar_v21_fk()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage
                 WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'merged_into_id'
                   AND referenced_table_name = 'users') THEN
    ALTER TABLE users ADD CONSTRAINT fk_users_merged_into_id FOREIGN KEY (merged_into_id) REFERENCES users(id);
  END IF;
END //
DELIMITER ;
CALL rahbar_v21_fk();
DROP PROCEDURE IF EXISTS rahbar_v21_fk;

-- ------------------------------------------------------------------ permissions
-- New screen: Alumni. Interview criteria are managed with Applications (edit).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'ALUMNI:VIEW', 'ALUMNI:EDIT')
 WHERE role_id IN (1, 2, 8) AND FIND_IN_SET('ALUMNI:VIEW', COALESCE(permissions, '')) = 0;
