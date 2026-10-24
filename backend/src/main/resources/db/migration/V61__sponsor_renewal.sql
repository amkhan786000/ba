-- Sponsor renewal campaigns (Sponsors & Payments > Sponsor Renewal): before a new session the office asks every
-- active sponsor whether they will continue; sponsors answer by email link / in their portal, or the office records
-- the answer after a call. Re-runnable.
CREATE TABLE IF NOT EXISTS renewal_campaigns (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_year INT NOT NULL,
  deadline DATE NULL,
  message VARCHAR(2000) NULL,
  closed_at DATETIME(6) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One sponsor in a campaign: asked (emailed / reminded) and their answer.
CREATE TABLE IF NOT EXISTS renewal_responses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  campaign_id BIGINT NOT NULL,
  sponsor_id BIGINT NOT NULL,
  current_students INT NOT NULL DEFAULT 0,
  emailed_at DATETIME(6) NULL,
  reminded_at DATETIME(6) NULL,
  answer VARCHAR(10) NULL,
  students_wanted INT NULL,
  note VARCHAR(500) NULL,
  answered_at DATETIME(6) NULL,
  answered_by BIGINT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_renewal_response (campaign_id, sponsor_id),
  KEY idx_renewal_response_sponsor (sponsor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
