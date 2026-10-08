-- Broadcast messages: saved templates, and sending at a chosen time.

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
-- When to send (NULL: sent straight away); recipients are worked out at sending time from audience_json.
CALL rahbar_add_column('broadcast_messages', 'scheduled_at', 'DATETIME(6) NULL');
CALL rahbar_add_column('broadcast_messages', 'audience_json', 'TEXT NULL');
DROP PROCEDURE IF EXISTS rahbar_add_column;

CREATE TABLE IF NOT EXISTS broadcast_templates (
  template_id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (template_id),
  UNIQUE KEY uq_broadcast_template_name (name),
  CONSTRAINT fk_broadcast_templates_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_broadcast_templates_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
