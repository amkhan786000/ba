-- Sponsors & Payments > Sponsor Follow-up: calls, notes and emails to sponsors who stopped paying or have no student
-- now, so the office sees who was contacted, when and by whom (and doesn't ask the same sponsor twice in a week).
-- kind: LAPSED (installments overdue) or AVAILABLE (no current student). channel: EMAIL, CALL or NOTE.
CREATE TABLE IF NOT EXISTS sponsor_followups (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sponsor_user_id BIGINT NOT NULL,
  kind VARCHAR(10) NOT NULL,
  channel VARCHAR(10) NOT NULL,
  note VARCHAR(1000) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_sponsor_followups_sponsor (sponsor_user_id),
  CONSTRAINT fk_sponsor_followups_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
