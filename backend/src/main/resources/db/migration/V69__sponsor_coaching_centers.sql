-- Sponsors who run / pay for a coaching center (RCC): chosen on Edit Sponsor (several centers allowed) and shown on
-- the Coaching Centers page. Re-runnable.
CREATE TABLE IF NOT EXISTS sponsor_coaching_centers (
  sponsor_user_id BIGINT NOT NULL,
  rcc_center_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (sponsor_user_id, rcc_center_id),
  KEY idx_sponsor_coaching_centers_center (rcc_center_id),
  CONSTRAINT fk_sponsor_coaching_centers_sponsor FOREIGN KEY (sponsor_user_id) REFERENCES users(id) ON DELETE CASCADE
);
